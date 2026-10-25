package com.stockflow.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "restock_proposals")
class RestockProposal {

    @Id private UUID id;
    @Column(name = "store_id", nullable = false) private UUID storeId;
    @Column(name = "created_by_user_id", nullable = false) private UUID createdByUserId;
    @Column(name = "create_idempotency_key", nullable = false, length = 200) private String createIdempotencyKey;
    @Column(name = "create_request_hash", nullable = false, length = 64) private String createRequestHash;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "source_report_id", nullable = false) private UUID sourceReportId;
    @Column(name = "source_report_version", nullable = false) private int sourceReportVersion;
    @Column(name = "recommendation_id", nullable = false) private UUID recommendationId;
    @Column(name = "evidence_id", nullable = false) private UUID evidenceId;
    @Column(name = "evidence_hash", nullable = false, length = 64) private String evidenceHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RestockProposalStatus status;
    @Column(name = "proposed_quantity", nullable = false) private long proposedQuantity;
    @Column(nullable = false, length = 1000) private String rationale;
    @Column(nullable = false, length = 2000) private String assumptions;
    @Column(name = "snapshot_available", nullable = false) private long snapshotAvailable;
    @Column(name = "snapshot_reserved", nullable = false) private long snapshotReserved;
    @Column(name = "snapshot_reorder_point", nullable = false) private long snapshotReorderPoint;
    @Column(name = "snapshot_inventory_version", nullable = false) private long snapshotInventoryVersion;
    @Column(name = "snapshot_inventory_updated_at", nullable = false) private Instant snapshotInventoryUpdatedAt;
    @Column(name = "source_data_as_of", nullable = false) private Instant sourceDataAsOf;
    @Column(name = "model_provider", length = 40) private String modelProvider;
    @Column(name = "model_name", length = 160) private String modelName;
    @Column(name = "model_input_tokens") private Integer modelInputTokens;
    @Column(name = "model_output_tokens") private Integer modelOutputTokens;
    @Column(name = "model_total_tokens") private Integer modelTotalTokens;
    @Column(name = "model_estimated_cost_usd", precision = 19, scale = 8) private BigDecimal modelEstimatedCostUsd;
    @Column(name = "model_pricing_version", length = 40) private String modelPricingVersion;
    @Column(name = "model_latency_ms") private Long modelLatencyMs;
    @Column(name = "approved_by_user_id") private UUID approvedByUserId;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "approved_proposal_version") private Long approvedProposalVersion;
    @Column(name = "decision_reason", length = 500) private String decisionReason;
    @Column(name = "execution_command_id") private UUID executionCommandId;
    @Column(name = "execution_owner") private UUID executionOwner;
    @Column(name = "execution_started_at") private Instant executionStartedAt;
    @Column(name = "execution_lease_until") private Instant executionLeaseUntil;
    @Column(name = "inventory_adjustment_id") private UUID inventoryAdjustmentId;
    @Column(name = "executed_at") private Instant executedAt;
    @Column(name = "failure_code", length = 80) private String failureCode;
    @Column(name = "stale_reason", length = 80) private String staleReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected RestockProposal() {}

    RestockProposal(
            UUID id, UUID storeId, UUID creator, String key, String requestHash, UUID productId,
            UUID sourceReportId, int sourceReportVersion, UUID recommendationId, UUID evidenceId,
            String evidenceHash, long proposedQuantity, String rationale, String assumptions,
            long available, long reserved, long reorderPoint, long inventoryVersion,
            Instant inventoryUpdatedAt, Instant sourceDataAsOf, String modelProvider, String modelName,
            Integer modelInputTokens, Integer modelOutputTokens, Integer modelTotalTokens,
            BigDecimal modelEstimatedCostUsd, String modelPricingVersion, long modelLatencyMs,
            Instant now) {
        this.id = id;
        this.storeId = storeId;
        this.createdByUserId = creator;
        this.createIdempotencyKey = key;
        this.createRequestHash = requestHash;
        this.productId = productId;
        this.sourceReportId = sourceReportId;
        this.sourceReportVersion = sourceReportVersion;
        this.recommendationId = recommendationId;
        this.evidenceId = evidenceId;
        this.evidenceHash = evidenceHash;
        this.status = RestockProposalStatus.PROPOSED;
        this.proposedQuantity = proposedQuantity;
        this.rationale = rationale;
        this.assumptions = assumptions;
        this.snapshotAvailable = available;
        this.snapshotReserved = reserved;
        this.snapshotReorderPoint = reorderPoint;
        this.snapshotInventoryVersion = inventoryVersion;
        this.snapshotInventoryUpdatedAt = inventoryUpdatedAt;
        this.sourceDataAsOf = sourceDataAsOf;
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        this.modelInputTokens = modelInputTokens;
        this.modelOutputTokens = modelOutputTokens;
        this.modelTotalTokens = modelTotalTokens;
        this.modelEstimatedCostUsd = modelEstimatedCostUsd;
        this.modelPricingVersion = modelPricingVersion;
        this.modelLatencyMs = modelLatencyMs;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void approve(UUID actor, long expectedVersion, Instant now) {
        require(RestockProposalStatus.PROPOSED, expectedVersion);
        approvedByUserId = actor;
        approvedAt = now;
        approvedProposalVersion = version + 1;
        transition(RestockProposalStatus.APPROVED, now);
    }

    void reject(long expectedVersion, String reason, Instant now) {
        require(RestockProposalStatus.PROPOSED, expectedVersion);
        decisionReason = reason;
        transition(RestockProposalStatus.REJECTED, now);
    }

    void cancel(UUID actor, boolean admin, long expectedVersion, String reason, Instant now) {
        if (version != expectedVersion || !(status == RestockProposalStatus.PROPOSED
                || (admin && status == RestockProposalStatus.APPROVED))) {
            throw new IllegalStateException("Proposal cannot be cancelled from its current state");
        }
        if (!admin && !createdByUserId.equals(actor)) throw new IllegalStateException("Only the creator may cancel");
        decisionReason = reason;
        transition(RestockProposalStatus.CANCELLED, now);
    }

    void claim(UUID commandId, UUID owner, Instant leaseUntil, Instant now) {
        if (status != RestockProposalStatus.APPROVED && status != RestockProposalStatus.FAILED) {
            throw new IllegalStateException("Only approved or failed proposals can execute");
        }
        if (approvedProposalVersion == null || approvedProposalVersion != version) {
            throw new IllegalStateException("Approval does not match the current proposal version");
        }
        executionCommandId = commandId;
        executionOwner = owner;
        executionStartedAt = now;
        executionLeaseUntil = leaseUntil;
        failureCode = null;
        transition(RestockProposalStatus.EXECUTING, now);
    }

    void executed(UUID owner, UUID adjustmentId, Instant now) {
        requireExecutionOwner(owner, now);
        inventoryAdjustmentId = adjustmentId;
        executedAt = now;
        executionOwner = null;
        executionLeaseUntil = null;
        transition(RestockProposalStatus.EXECUTED, now);
    }

    void stale(UUID owner, String reason, Instant now) {
        requireExecutionOwner(owner, now);
        staleReason = reason;
        executionOwner = null;
        executionLeaseUntil = null;
        transition(RestockProposalStatus.STALE, now);
    }

    void failed(UUID owner, String code, Instant now) {
        if (status != RestockProposalStatus.EXECUTING || !owner.equals(executionOwner)) return;
        failureCode = code;
        executionOwner = null;
        executionLeaseUntil = null;
        transition(RestockProposalStatus.FAILED, now);
        approvedProposalVersion = version + 1;
    }

    void recover(Instant now) {
        if (status != RestockProposalStatus.EXECUTING || executionLeaseUntil == null
                || executionLeaseUntil.isAfter(now)) throw new IllegalStateException("Execution lease is still active");
        executionOwner = null;
        executionLeaseUntil = null;
        executionCommandId = null;
        transition(RestockProposalStatus.APPROVED, now);
        approvedProposalVersion = version + 1;
    }

    void recoverExecuted(UUID adjustmentId, Instant now) {
        if (status != RestockProposalStatus.EXECUTING || executionLeaseUntil == null
                || executionLeaseUntil.isAfter(now)) throw new IllegalStateException("Execution lease is still active");
        inventoryAdjustmentId = adjustmentId;
        executedAt = now;
        executionOwner = null;
        executionLeaseUntil = null;
        transition(RestockProposalStatus.EXECUTED, now);
    }

    private void require(RestockProposalStatus expected, long expectedVersion) {
        if (status != expected || version != expectedVersion) {
            throw new IllegalStateException("Proposal state or version changed");
        }
    }

    private void requireExecutionOwner(UUID owner, Instant now) {
        if (status != RestockProposalStatus.EXECUTING || !owner.equals(executionOwner)
                || executionLeaseUntil == null || !now.isBefore(executionLeaseUntil)) {
            throw new IllegalStateException("Execution lease is no longer valid");
        }
    }

    private void transition(RestockProposalStatus next, Instant now) {
        status = next;
        updatedAt = now;
    }

    UUID getId() { return id; }
    UUID getStoreId() { return storeId; }
    UUID getCreatedByUserId() { return createdByUserId; }
    String getCreateRequestHash() { return createRequestHash; }
    UUID getProductId() { return productId; }
    UUID getSourceReportId() { return sourceReportId; }
    int getSourceReportVersion() { return sourceReportVersion; }
    UUID getRecommendationId() { return recommendationId; }
    UUID getEvidenceId() { return evidenceId; }
    String getEvidenceHash() { return evidenceHash; }
    RestockProposalStatus getStatus() { return status; }
    long getProposedQuantity() { return proposedQuantity; }
    String getRationale() { return rationale; }
    String getAssumptions() { return assumptions; }
    long getSnapshotAvailable() { return snapshotAvailable; }
    long getSnapshotReserved() { return snapshotReserved; }
    long getSnapshotReorderPoint() { return snapshotReorderPoint; }
    long getSnapshotInventoryVersion() { return snapshotInventoryVersion; }
    Instant getSnapshotInventoryUpdatedAt() { return snapshotInventoryUpdatedAt; }
    Instant getSourceDataAsOf() { return sourceDataAsOf; }
    String getModelProvider() { return modelProvider; }
    String getModelName() { return modelName; }
    UUID getApprovedByUserId() { return approvedByUserId; }
    Instant getApprovedAt() { return approvedAt; }
    Long getApprovedProposalVersion() { return approvedProposalVersion; }
    String getDecisionReason() { return decisionReason; }
    UUID getExecutionCommandId() { return executionCommandId; }
    UUID getExecutionOwner() { return executionOwner; }
    Instant getExecutionLeaseUntil() { return executionLeaseUntil; }
    UUID getInventoryAdjustmentId() { return inventoryAdjustmentId; }
    Instant getExecutedAt() { return executedAt; }
    String getFailureCode() { return failureCode; }
    String getStaleReason() { return staleReason; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
    long getVersion() { return version; }
}
