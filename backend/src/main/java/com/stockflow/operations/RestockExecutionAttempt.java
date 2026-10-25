package com.stockflow.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restock_execution_attempts")
class RestockExecutionAttempt {
    enum Status { STARTED, SUCCEEDED, FAILED, ABANDONED }

    @Id private UUID id;
    @Column(name = "proposal_id", nullable = false) private UUID proposalId;
    @Column(name = "store_id", nullable = false) private UUID storeId;
    @Column(name = "command_id", nullable = false) private UUID commandId;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Column(name = "attempt_number", nullable = false) private int attemptNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "lease_until", nullable = false) private Instant leaseUntil;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "inventory_adjustment_id") private UUID inventoryAdjustmentId;
    @Column(name = "error_code", length = 80) private String errorCode;

    protected RestockExecutionAttempt() {}

    RestockExecutionAttempt(UUID proposalId, UUID storeId, UUID commandId, String requestHash,
            int attemptNumber, UUID owner, Instant startedAt, Instant leaseUntil) {
        this.id = UUID.randomUUID(); this.proposalId = proposalId; this.storeId = storeId;
        this.commandId = commandId; this.requestHash = requestHash; this.attemptNumber = attemptNumber;
        this.status = Status.STARTED; this.ownerId = owner; this.startedAt = startedAt; this.leaseUntil = leaseUntil;
    }

    void succeed(UUID adjustmentId, Instant now) { status = Status.SUCCEEDED; inventoryAdjustmentId = adjustmentId; completedAt = now; }
    void fail(String code, Instant now) { status = Status.FAILED; errorCode = code; completedAt = now; }
    void abandon(String code, Instant now) { status = Status.ABANDONED; errorCode = code; completedAt = now; }
    UUID getId() { return id; }
    UUID getCommandId() { return commandId; }
    String getRequestHash() { return requestHash; }
    Status getStatus() { return status; }
    int getAttemptNumber() { return attemptNumber; }
    UUID getInventoryAdjustmentId() { return inventoryAdjustmentId; }
    String getErrorCode() { return errorCode; }
    Instant getStartedAt() { return startedAt; }
    Instant getCompletedAt() { return completedAt; }
}
