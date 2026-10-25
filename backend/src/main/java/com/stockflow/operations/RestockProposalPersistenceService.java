package com.stockflow.operations;

import com.stockflow.analysis.BusinessAnalysisReport;
import com.stockflow.analysis.BusinessReportService;
import com.stockflow.analytics.AnalyticsModels;
import com.stockflow.analytics.AnalyticsService;
import com.stockflow.common.error.ApiException;
import com.stockflow.common.error.ApiExceptions;
import com.stockflow.inventory.InventoryAdjustmentRepository;
import com.stockflow.inventory.InventoryService;
import com.stockflow.order.OutboxEvent;
import com.stockflow.order.OutboxEventRepository;
import com.stockflow.product.ProductService;
import com.stockflow.product.ProductStatus;
import com.stockflow.store.StoreContext;
import com.stockflow.store.StoreRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class RestockProposalPersistenceService {
    private final RestockProposalRepository proposals;
    private final RestockProposalEventRepository events;
    private final RestockExecutionAttemptRepository attempts;
    private final InventoryAdjustmentRepository adjustments;
    private final InventoryService inventory;
    private final ProductService products;
    private final BusinessReportService reports;
    private final AnalyticsService analytics;
    private final OutboxEventRepository outbox;
    private final Clock clock;
    private final RestockProposalConfiguration.Properties properties;

    RestockProposalPersistenceService(
            RestockProposalRepository proposals, RestockProposalEventRepository events,
            RestockExecutionAttemptRepository attempts, InventoryAdjustmentRepository adjustments,
            InventoryService inventory, ProductService products, BusinessReportService reports, AnalyticsService analytics,
            OutboxEventRepository outbox, Clock clock,
            RestockProposalConfiguration.Properties properties) {
        this.proposals = proposals; this.events = events; this.attempts = attempts; this.adjustments = adjustments;
        this.inventory = inventory; this.products = products; this.reports = reports; this.analytics = analytics; this.outbox = outbox;
        this.clock = clock; this.properties = properties;
    }

    @Transactional
    List<RestockProposal> create(StoreContext context, String key, String hash, List<Draft> drafts) {
        var existing = proposals.findByStoreIdAndCreatedByUserIdAndCreateIdempotencyKeyOrderByProductId(
                context.storeId(), context.userId(), key);
        if (!existing.isEmpty()) {
            if (existing.stream().anyMatch(item -> !item.getCreateRequestHash().equals(hash))) {
                throw ApiExceptions.conflict("RESTOCK_IDEMPOTENCY_CONFLICT",
                        "The idempotency key was already used with another request");
            }
            return existing;
        }
        var now = Instant.now(clock);
        try {
            var created = drafts.stream().map(draft -> new RestockProposal(
                    UUID.randomUUID(), context.storeId(), context.userId(), key, hash, draft.productId(),
                    draft.reportId(), draft.reportVersion(), draft.recommendationId(), draft.evidenceId(),
                    draft.evidenceHash(), draft.quantity(), draft.rationale(), draft.assumptions(),
                    draft.available(), draft.reserved(), draft.reorderPoint(), draft.inventoryVersion(),
                    draft.inventoryUpdatedAt(), draft.dataAsOf(), draft.modelProvider(), draft.modelName(),
                    draft.modelInputTokens(), draft.modelOutputTokens(), draft.modelTotalTokens(),
                    draft.modelEstimatedCostUsd(), draft.modelPricingVersion(), draft.modelLatencyMs(), now)).toList();
            proposals.saveAll(created);
            created.forEach(item -> events.save(event(item, null, RestockProposalStatus.PROPOSED,
                    "CREATED", context, null, UUID.randomUUID(), 0, now)));
            proposals.flush();
            return created;
        } catch (DataIntegrityViolationException exception) {
            throw ApiExceptions.conflict("RESTOCK_IDEMPOTENCY_CONFLICT", "The proposal command was already processed");
        }
    }

    @Transactional(readOnly = true)
    List<RestockProposal> replay(StoreContext context, String key, String hash) {
        var existing = proposals.findByStoreIdAndCreatedByUserIdAndCreateIdempotencyKeyOrderByProductId(
                context.storeId(), context.userId(), key);
        if (!existing.isEmpty() && existing.stream().anyMatch(item -> !item.getCreateRequestHash().equals(hash))) {
            throw ApiExceptions.conflict("RESTOCK_IDEMPOTENCY_CONFLICT",
                    "The idempotency key was already used with another request");
        }
        return existing;
    }

    @Transactional(readOnly = true)
    RestockProposal get(StoreContext context, UUID id) {
        return visible(context, id);
    }

    @Transactional(readOnly = true)
    List<RestockProposal> list(StoreContext context, int limit) {
        var page = PageRequest.of(0, Math.min(limit, 100));
        return context.role() == StoreRole.ADMIN
                ? proposals.findByStoreIdOrderByCreatedAtDescIdDesc(context.storeId(), page)
                : proposals.findByStoreIdAndCreatedByUserIdOrderByCreatedAtDescIdDesc(
                        context.storeId(), context.userId(), page);
    }

    @Transactional
    RestockProposal approve(StoreContext context, UUID id, long expectedVersion) {
        requireAdmin(context);
        var proposal = lock(context, id);
        var from = proposal.getStatus(); var now = Instant.now(clock);
        try { proposal.approve(context.userId(), expectedVersion, now); }
        catch (IllegalStateException exception) { throw conflict(exception); }
        proposals.flush();
        events.save(event(proposal, from, proposal.getStatus(), "APPROVED", context, null,
                UUID.randomUUID(), proposal.getVersion(), now));
        return proposal;
    }

    @Transactional
    RestockProposal reject(StoreContext context, UUID id, long expectedVersion, String reason) {
        requireAdmin(context);
        var proposal = lock(context, id);
        var from = proposal.getStatus(); var now = Instant.now(clock);
        try { proposal.reject(expectedVersion, trim(reason), now); }
        catch (IllegalStateException exception) { throw conflict(exception); }
        proposals.flush();
        events.save(event(proposal, from, proposal.getStatus(), "REJECTED", context, "USER_REJECTED",
                UUID.randomUUID(), proposal.getVersion(), now));
        return proposal;
    }

    @Transactional
    RestockProposal cancel(StoreContext context, UUID id, long expectedVersion, String reason) {
        var proposal = lockVisible(context, id);
        var from = proposal.getStatus(); var now = Instant.now(clock);
        try { proposal.cancel(context.userId(), context.role() == StoreRole.ADMIN, expectedVersion, trim(reason), now); }
        catch (IllegalStateException exception) { throw conflict(exception); }
        proposals.flush();
        events.save(event(proposal, from, proposal.getStatus(), "CANCELLED", context, "USER_CANCELLED",
                UUID.randomUUID(), proposal.getVersion(), now));
        return proposal;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ClaimResult claim(StoreContext context, UUID id, UUID commandId, String requestHash, long expectedVersion) {
        requireAdmin(context);
        var prior = attempts.findByStoreIdAndProposalIdAndCommandId(context.storeId(), id, commandId);
        if (prior.isPresent()) {
            if (!prior.get().getRequestHash().equals(requestHash)) {
                throw ApiExceptions.conflict("RESTOCK_COMMAND_CONFLICT", "Command ID was reused with another payload");
            }
            if (prior.get().getStatus() == RestockExecutionAttempt.Status.SUCCEEDED) {
                return new ClaimResult(lock(context, id), null, true);
            }
            throw ApiExceptions.conflict("RESTOCK_COMMAND_ALREADY_ATTEMPTED", "This receipt command is not retryable");
        }
        var proposal = lock(context, id);
        if (proposal.getVersion() != expectedVersion) throw conflict(new IllegalStateException("Proposal version changed"));
        var now = Instant.now(clock); var owner = UUID.randomUUID(); var from = proposal.getStatus();
        try { proposal.claim(commandId, owner, now.plus(properties.executionLease()), now); }
        catch (IllegalStateException exception) { throw conflict(exception); }
        var attempt = new RestockExecutionAttempt(id, context.storeId(), commandId, requestHash,
                Math.toIntExact(attempts.countByProposalId(id) + 1), owner, now, now.plus(properties.executionLease()));
        attempts.save(attempt);
        proposals.flush();
        events.save(event(proposal, from, RestockProposalStatus.EXECUTING, "EXECUTION_CLAIMED", context,
                null, commandId, proposal.getVersion(), now));
        return new ClaimResult(proposal, owner, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ExecutionOutcome apply(StoreContext context, UUID id, UUID owner, UUID commandId) {
        var proposal = lock(context, id);
        String stale = freshness(context, proposal);
        var attempt = attempts.findByStoreIdAndProposalIdAndCommandId(context.storeId(), id, commandId).orElseThrow();
        var now = Instant.now(clock);
        if (stale != null) {
            proposal.stale(owner, stale, now);
            attempt.fail(stale, now);
            proposals.flush();
            events.save(event(proposal, RestockProposalStatus.EXECUTING, RestockProposalStatus.STALE,
                    "MARKED_STALE", context, stale, commandId, proposal.getVersion(), now));
            return new ExecutionOutcome(proposal, stale);
        }
        var receipt = inventory.receiveRestock(
                context.storeId(), proposal.getProductId(), proposal.getProposedQuantity(),
                proposal.getSnapshotInventoryVersion(), proposal.getSnapshotAvailable(),
                proposal.getSnapshotReserved(), proposal.getSnapshotReorderPoint(), context.userId(), commandId);
        proposal.executed(owner, receipt.adjustmentId(), now);
        attempt.succeed(receipt.adjustmentId(), now);
        outbox.save(new OutboxEvent(UUID.randomUUID(), context.storeId(), "RESTOCK_PROPOSAL", id,
                "RESTOCK_RECEIPT_RECORDED", "{\"proposalId\":\"" + id + "\",\"commandId\":\""
                        + commandId + "\",\"adjustmentId\":\"" + receipt.adjustmentId() + "\"}", now));
        proposals.flush();
        events.save(event(proposal, RestockProposalStatus.EXECUTING, RestockProposalStatus.EXECUTED,
                "RECEIPT_RECORDED", context, null, commandId, proposal.getVersion(), now));
        return new ExecutionOutcome(proposal, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void fail(StoreContext context, UUID id, UUID owner, UUID commandId, String code) {
        var proposal = proposals.lockOwned(id, context.storeId()).orElse(null);
        if (proposal == null) return;
        var now = Instant.now(clock); proposal.failed(owner, code, now);
        attempts.findByStoreIdAndProposalIdAndCommandId(context.storeId(), id, commandId)
                .ifPresent(attempt -> attempt.fail(code, now));
        proposals.flush();
        events.save(event(proposal, RestockProposalStatus.EXECUTING, proposal.getStatus(),
                "EXECUTION_FAILED", context, code, commandId, proposal.getVersion(), now));
    }

    @Transactional(readOnly = true)
    RestockProposalModels.HistoryResponse history(StoreContext context, UUID id) {
        visible(context, id);
        var eventItems = events.findByStoreIdAndProposalIdOrderByCreatedAtAscIdAsc(context.storeId(), id).stream()
                .map(item -> new RestockProposalModels.EventResponse(item.getId(), item.getFromStatus(),
                        item.getToStatus(), item.getAction(), item.getActorUserId(), item.getReasonCode(), item.getCreatedAt()))
                .toList();
        var attemptItems = attempts.findByStoreIdAndProposalIdOrderByStartedAtDescIdDesc(context.storeId(), id).stream()
                .map(item -> new RestockProposalModels.AttemptResponse(item.getId(), item.getCommandId(),
                        item.getStatus().name(), item.getAttemptNumber(), item.getInventoryAdjustmentId(),
                        item.getErrorCode(), item.getStartedAt(), item.getCompletedAt())).toList();
        return new RestockProposalModels.HistoryResponse(eventItems, attemptItems);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int reconcileExpired() {
        var now = Instant.now(clock);
        var expired = proposals.findExpired(now, PageRequest.of(0, properties.recoveryBatchSize()));
        int count = 0;
        for (var candidate : expired) {
            var proposal = proposals.lockOwned(candidate.getId(), candidate.getStoreId()).orElse(null);
            if (proposal == null || proposal.getStatus() != RestockProposalStatus.EXECUTING
                    || proposal.getExecutionLeaseUntil().isAfter(now)) continue;
            var commandId = proposal.getExecutionCommandId();
            var adjustment = adjustments.findByOperationCommandId(commandId);
            var attempt = attempts.findByStoreIdAndProposalIdAndCommandId(
                    proposal.getStoreId(), proposal.getId(), commandId).orElse(null);
            if (adjustment.isPresent()) {
                proposal.recoverExecuted(adjustment.get().getId(), now);
                if (attempt != null) attempt.succeed(adjustment.get().getId(), now);
            } else {
                proposal.recover(now);
                if (attempt != null) attempt.abandon("EXECUTION_LEASE_EXPIRED", now);
            }
            proposals.flush();
            events.save(new RestockProposalEvent(proposal.getId(), proposal.getStoreId(),
                    RestockProposalStatus.EXECUTING, proposal.getStatus(), "EXECUTION_RECOVERED", null,
                    StoreRole.ADMIN, "EXECUTION_LEASE_EXPIRED", UUID.randomUUID(), proposal.getVersion(), now));
            count++;
        }
        return count;
    }

    private String freshness(StoreContext context, RestockProposal proposal) {
        if (proposal.getSourceDataAsOf().plus(properties.freshnessWindow()).isBefore(Instant.now(clock))) {
            return "SOURCE_ANALYSIS_EXPIRED";
        }
        var report = reports.get(context, proposal.getSourceReportId());
        if (report.report() == null || report.metadata().status() != BusinessAnalysisReport.Status.COMPLETED
                || report.report().reportVersion() != proposal.getSourceReportVersion()) return "SOURCE_REPORT_CHANGED";
        var recommendation = report.report().recommendations().stream()
                .filter(item -> item.id().equals(proposal.getRecommendationId())).findFirst().orElse(null);
        if (recommendation == null || !recommendation.evidenceIds().contains(proposal.getEvidenceId())) {
            return "SOURCE_EVIDENCE_CHANGED";
        }
        var evidence = report.report().evidence().stream()
                .filter(item -> item.id().equals(proposal.getEvidenceId())).findFirst().orElse(null);
        if (evidence == null || !RestockProposalService.evidenceHash(evidence).equals(proposal.getEvidenceHash())) {
            return "SOURCE_EVIDENCE_CHANGED";
        }
        var product = products.requireOwnedProduct(context.storeId(), proposal.getProductId());
        if (product.getStatus() != ProductStatus.ACTIVE) return "PRODUCT_INACTIVE";
        var current = inventory.snapshot(context.storeId(), proposal.getProductId());
        if (current.version() != proposal.getSnapshotInventoryVersion()
                || current.availableQuantity() != proposal.getSnapshotAvailable()
                || current.reservedQuantity() != proposal.getSnapshotReserved()
                || current.reorderPoint() != proposal.getSnapshotReorderPoint()
                || !sameInstant(current.updatedAt(), proposal.getSnapshotInventoryUpdatedAt())) {
            return "INVENTORY_CHANGED";
        }
        var currentRisk = analytics.getInventoryRisk(context, AnalyticsModels.PeriodPreset.CUSTOM,
                report.report().periodStart(), report.report().periodEndExclusive(), 100).items().stream()
                .filter(item -> item.productId().equals(proposal.getProductId())).findFirst().orElse(null);
        if (currentRisk == null || currentRisk.riskLevel() == AnalyticsModels.InventoryRiskLevel.HEALTHY
                || currentRisk.riskLevel() == AnalyticsModels.InventoryRiskLevel.NO_SALES) {
            return "INVENTORY_RISK_RESOLVED";
        }
        return null;
    }

    private boolean sameInstant(Instant one, Instant two) {
        return Duration.between(one, two).abs().compareTo(Duration.ofNanos(1_000)) < 0;
    }
    private RestockProposal visible(StoreContext context, UUID id) {
        return (context.role() == StoreRole.ADMIN
                ? proposals.findByIdAndStoreId(id, context.storeId())
                : proposals.findByIdAndStoreIdAndCreatedByUserId(id, context.storeId(), context.userId()))
                .orElseThrow(() -> ApiExceptions.notFound("Restock proposal"));
    }
    private RestockProposal lock(StoreContext context, UUID id) {
        return proposals.lockOwned(id, context.storeId()).orElseThrow(() -> ApiExceptions.notFound("Restock proposal"));
    }
    private RestockProposal lockVisible(StoreContext context, UUID id) {
        var item = lock(context, id);
        if (context.role() != StoreRole.ADMIN && !item.getCreatedByUserId().equals(context.userId())) {
            throw ApiExceptions.notFound("Restock proposal");
        }
        return item;
    }
    private void requireAdmin(StoreContext context) {
        if (context.role() != StoreRole.ADMIN) throw ApiExceptions.forbidden("ADMIN role is required");
    }
    private ApiException conflict(IllegalStateException exception) {
        return ApiExceptions.conflict("RESTOCK_STATE_CONFLICT", exception.getMessage());
    }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private RestockProposalEvent event(RestockProposal proposal, RestockProposalStatus from,
            RestockProposalStatus to, String action, StoreContext context, String reason,
            UUID requestId, long version, Instant now) {
        return new RestockProposalEvent(proposal.getId(), proposal.getStoreId(), from, to, action,
                context.userId(), context.role(), reason, requestId, version, now);
    }

    record Draft(UUID productId, UUID reportId, int reportVersion, UUID recommendationId, UUID evidenceId,
            String evidenceHash, long quantity, String rationale, String assumptions, long available,
            long reserved, long reorderPoint, long inventoryVersion, Instant inventoryUpdatedAt,
            Instant dataAsOf, String modelProvider, String modelName, Integer modelInputTokens,
            Integer modelOutputTokens, Integer modelTotalTokens, java.math.BigDecimal modelEstimatedCostUsd,
            String modelPricingVersion, long modelLatencyMs) {}
    record ClaimResult(RestockProposal proposal, UUID owner, boolean replayed) {}
    record ExecutionOutcome(RestockProposal proposal, String errorCode) {}
}
