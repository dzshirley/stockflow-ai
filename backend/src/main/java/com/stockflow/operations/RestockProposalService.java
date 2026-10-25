package com.stockflow.operations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.ai.RestockProposalAiService;
import com.stockflow.analysis.BusinessAnalysisReport;
import com.stockflow.analysis.BusinessReportService;
import com.stockflow.analysis.EvidenceReference;
import com.stockflow.analytics.AnalyticsModels;
import com.stockflow.analytics.AnalyticsService;
import com.stockflow.common.error.ApiException;
import com.stockflow.common.error.ApiExceptions;
import com.stockflow.inventory.InventoryService;
import com.stockflow.product.ProductService;
import com.stockflow.store.StoreContext;
import com.stockflow.store.StoreRole;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RestockProposalService {
    private static final Logger log = LoggerFactory.getLogger(RestockProposalService.class);
    private final BusinessReportService reports;
    private final AnalyticsService analytics;
    private final RestockProposalAiService ai;
    private final InventoryService inventory;
    private final ProductService products;
    private final RestockProposalPersistenceService persistence;
    private final RestockProposalConfiguration.Properties properties;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;
    private final Clock clock;

    RestockProposalService(
            BusinessReportService reports, AnalyticsService analytics, RestockProposalAiService ai,
            InventoryService inventory, ProductService products, RestockProposalPersistenceService persistence,
            RestockProposalConfiguration.Properties properties, ObjectMapper mapper, MeterRegistry meters, Clock clock) {
        this.reports = reports; this.analytics = analytics; this.ai = ai; this.inventory = inventory;
        this.products = products; this.persistence = persistence; this.properties = properties;
        this.mapper = mapper; this.meters = meters; this.clock = clock;
    }

    public RestockProposalModels.ProposalList generate(
            StoreContext context, String rawKey, RestockProposalModels.GenerateRequest request) {
        String key = key(rawKey);
        String requestHash = hash(request);
        var replay = persistence.replay(context, key, requestHash);
        if (!replay.isEmpty()) return listResponse(context, replay);
        var reportDetail = reports.get(context, request.sourceReportId());
        var report = reportDetail.report();
        if (report == null || report.status() != BusinessAnalysisReport.Status.COMPLETED) {
            throw ApiExceptions.conflict("RESTOCK_SOURCE_REPORT_INCOMPLETE", "A completed report is required");
        }
        if (report.dataAsOf().plus(properties.freshnessWindow()).isBefore(Instant.now(clock))) {
            throw ApiExceptions.conflict("RESTOCK_SOURCE_REPORT_STALE", "Regenerate the inventory analysis before proposing");
        }
        var recommendation = report.recommendations().stream()
                .filter(item -> item.id().equals(request.recommendationId())).findFirst()
                .orElseThrow(() -> ApiExceptions.badRequest("RESTOCK_RECOMMENDATION_INVALID",
                        "The recommendation is not part of the source report"));
        if (!recommendation.approvalRequired()) {
            throw ApiExceptions.badRequest("RESTOCK_RECOMMENDATION_INVALID", "The recommendation is not actionable");
        }
        Map<UUID, EvidenceReference> evidenceByProduct = report.evidence().stream()
                .filter(item -> recommendation.evidenceIds().contains(item.id()))
                .filter(item -> item.source() == EvidenceReference.Source.INVENTORY_SNAPSHOT)
                .filter(item -> item.metricKey().startsWith("inventory.available."))
                .collect(Collectors.toMap(item -> productId(item.metricKey()), Function.identity(), (a, b) -> a));
        if (evidenceByProduct.isEmpty()) {
            throw ApiExceptions.badRequest("RESTOCK_EVIDENCE_REQUIRED",
                    "The recommendation has no inventory-risk evidence");
        }
        var risk = analytics.getInventoryRisk(context, AnalyticsModels.PeriodPreset.CUSTOM,
                report.periodStart(), report.periodEndExclusive(), 100);
        var deterministic = risk.items().stream()
                .filter(item -> evidenceByProduct.containsKey(item.productId()))
                .filter(item -> item.riskLevel() == AnalyticsModels.InventoryRiskLevel.OUT_OF_STOCK
                        || item.riskLevel() == AnalyticsModels.InventoryRiskLevel.LOW_STOCK
                        || item.riskLevel() == AnalyticsModels.InventoryRiskLevel.AT_RISK)
                .toList();
        if (deterministic.isEmpty()) {
            throw ApiExceptions.conflict("RESTOCK_NO_CURRENT_RISK", "No referenced product is currently at risk");
        }
        var candidates = deterministic.stream().map(item -> new RestockProposalAiService.Candidate(
                item.productId(), item.sku(), item.riskLevel().name(), item.availableQuantity(),
                item.reservedQuantity(), item.reorderPoint(), item.averageDailyUnitsSold(), item.daysOfCover())).toList();
        var selection = ai.select(request.question().trim(), candidates);
        var byProduct = deterministic.stream().collect(Collectors.toMap(AnalyticsModels.InventoryRisk::productId, Function.identity()));
        var drafts = selection.productIds().stream().map(productId -> {
            var item = byProduct.get(productId);
            if (item == null) throw ApiExceptions.badRequest("RESTOCK_AI_OUTPUT_INVALID", "AI selected an unavailable product");
            var snapshot = inventory.snapshot(context.storeId(), productId);
            long targetFromVelocity = item.averageDailyUnitsSold().multiply(java.math.BigDecimal.valueOf(properties.targetCoverDays()))
                    .setScale(0, RoundingMode.CEILING).longValueExact() + snapshot.reservedQuantity();
            long target = Math.max(snapshot.reorderPoint() + 1, targetFromVelocity);
            long quantity = Math.max(1, target - snapshot.availableQuantity());
            if (quantity > properties.maxQuantity()) {
                throw ApiExceptions.conflict("RESTOCK_QUANTITY_EXCEEDS_LIMIT", "Calculated quantity exceeds the configured limit");
            }
            var evidence = evidenceByProduct.get(productId);
            return new RestockProposalPersistenceService.Draft(
                    productId, report.reportId(), report.reportVersion(), recommendation.id(), evidence.id(),
                    evidenceHash(evidence), quantity,
                    "Restore " + item.sku() + " to at least " + properties.targetCoverDays()
                            + " days of recent paid-sales coverage or above its reorder point.",
                    String.join(" | ", List.of(
                            "Recent paid-sales velocity is descriptive, not a forecast",
                            "Target coverage days: " + properties.targetCoverDays(),
                            "Physical receipt must be confirmed before inventory changes")),
                    snapshot.availableQuantity(), snapshot.reservedQuantity(), snapshot.reorderPoint(),
                    snapshot.version(), snapshot.updatedAt(), report.dataAsOf(), selection.provider(), selection.model(),
                    selection.inputTokens(), selection.outputTokens(), selection.totalTokens(),
                    selection.estimatedCostUsd(), selection.pricingVersion(), selection.latencyMs());
        }).toList();
        return listResponse(context, persistence.create(context, key, requestHash, drafts));
    }

    public RestockProposalModels.ProposalList list(StoreContext context, int limit) {
        return listResponse(context, persistence.list(context, limit));
    }
    public RestockProposalModels.ProposalResponse get(StoreContext context, UUID id) { return response(context, persistence.get(context, id)); }
    public RestockProposalModels.ProposalResponse approve(StoreContext context, UUID id, long version) {
        return response(context, persistence.approve(context, id, version));
    }
    public RestockProposalModels.ProposalResponse reject(StoreContext context, UUID id, long version, String reason) {
        return response(context, persistence.reject(context, id, version, reason));
    }
    public RestockProposalModels.ProposalResponse cancel(StoreContext context, UUID id, long version, String reason) {
        return response(context, persistence.cancel(context, id, version, reason));
    }
    public RestockProposalModels.ProposalResponse execute(
            StoreContext context, UUID id, UUID commandId, RestockProposalModels.ExecuteCommand command) {
        if (context.role() != StoreRole.ADMIN) throw ApiExceptions.forbidden("ADMIN role is required");
        if (!Boolean.TRUE.equals(command.received())) {
            throw ApiExceptions.badRequest("RESTOCK_RECEIPT_CONFIRMATION_REQUIRED",
                    "Confirm that the stock was physically received");
        }
        String requestHash = hash(command);
        var claim = persistence.claim(context, id, commandId, requestHash, command.expectedVersion());
        if (claim.replayed()) return response(context, claim.proposal());
        RestockProposalPersistenceService.ExecutionOutcome outcome;
        try {
            outcome = persistence.apply(context, id, claim.owner(), commandId);
        } catch (RuntimeException exception) {
            String code = safeCode(exception);
            persistence.fail(context, id, claim.owner(), commandId, code);
            meters.counter("stockflow.restock.execution.failures", "code", code).increment();
            log.warn("restock_execution_failed proposalId={} storeId={} commandId={} code={}",
                    id, context.storeId(), commandId, code);
            throw exception;
        }
        if (outcome.errorCode() != null) {
            meters.counter("stockflow.restock.stale", "reason", outcome.errorCode()).increment();
            throw ApiExceptions.conflict(outcome.errorCode(), "Proposal is stale; regenerate it from current data");
        }
        return response(context, outcome.proposal());
    }
    public RestockProposalModels.HistoryResponse history(StoreContext context, UUID id) {
        return persistence.history(context, id);
    }

    private RestockProposalModels.ProposalList listResponse(StoreContext context, List<RestockProposal> values) {
        return new RestockProposalModels.ProposalList(values.stream().map(item -> response(context, item)).toList());
    }
    private RestockProposalModels.ProposalResponse response(StoreContext context, RestockProposal item) {
        var product = products.requireOwnedProduct(context.storeId(), item.getProductId());
        return new RestockProposalModels.ProposalResponse(
                item.getId(), item.getProductId(), product.getSku(), product.getName(), item.getStatus(),
                item.getProposedQuantity(), item.getRationale(), List.of(item.getAssumptions().split(" \\| ")),
                item.getSourceReportId(), item.getSourceReportVersion(), item.getRecommendationId(), item.getEvidenceId(),
                item.getSnapshotAvailable(), item.getSnapshotReserved(), item.getSnapshotReorderPoint(),
                item.getSnapshotInventoryVersion(), item.getSnapshotInventoryUpdatedAt(), item.getSourceDataAsOf(),
                item.getCreatedByUserId(), item.getApprovedByUserId(), item.getApprovedAt(), item.getExecutionCommandId(),
                item.getInventoryAdjustmentId(), item.getExecutedAt(), item.getFailureCode(), item.getStaleReason(),
                item.getModelProvider(), item.getModelName(), item.getVersion(), item.getCreatedAt(), item.getUpdatedAt());
    }
    private String key(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw ApiExceptions.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain 1 to 200 characters");
        }
        return value.trim();
    }
    private String hash(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(value))); }
        catch (NoSuchAlgorithmException | JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }
    static String evidenceHash(EvidenceReference evidence) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(evidence.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private UUID productId(String key) {
        try { return UUID.fromString(key.substring("inventory.available.".length())); }
        catch (RuntimeException exception) { throw ApiExceptions.badRequest("RESTOCK_EVIDENCE_INVALID", "Inventory evidence is malformed"); }
    }
    private String safeCode(RuntimeException exception) {
        return exception instanceof ApiException api ? api.code() : exception.getClass().getSimpleName();
    }
}
