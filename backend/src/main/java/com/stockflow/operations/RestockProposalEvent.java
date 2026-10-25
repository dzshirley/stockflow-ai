package com.stockflow.operations;

import com.stockflow.store.StoreRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restock_proposal_events")
class RestockProposalEvent {
    @Id private UUID id;
    @Column(name = "proposal_id", nullable = false) private UUID proposalId;
    @Column(name = "store_id", nullable = false) private UUID storeId;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 20) private RestockProposalStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 20) private RestockProposalStatus toStatus;
    @Column(nullable = false, length = 40) private String action;
    @Column(name = "actor_user_id") private UUID actorUserId;
    @Enumerated(EnumType.STRING) @Column(name = "actor_role", nullable = false, length = 20) private StoreRole actorRole;
    @Column(name = "reason_code", length = 80) private String reasonCode;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "proposal_version", nullable = false) private long proposalVersion;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected RestockProposalEvent() {}

    RestockProposalEvent(UUID proposalId, UUID storeId, RestockProposalStatus from, RestockProposalStatus to,
            String action, UUID actor, StoreRole role, String reason, UUID requestId, long version, Instant now) {
        this.id = UUID.randomUUID(); this.proposalId = proposalId; this.storeId = storeId;
        this.fromStatus = from; this.toStatus = to; this.action = action; this.actorUserId = actor;
        this.actorRole = role; this.reasonCode = reason; this.requestId = requestId;
        this.proposalVersion = version; this.createdAt = now;
    }

    UUID getId() { return id; }
    RestockProposalStatus getFromStatus() { return fromStatus; }
    RestockProposalStatus getToStatus() { return toStatus; }
    String getAction() { return action; }
    UUID getActorUserId() { return actorUserId; }
    String getReasonCode() { return reasonCode; }
    Instant getCreatedAt() { return createdAt; }
}
