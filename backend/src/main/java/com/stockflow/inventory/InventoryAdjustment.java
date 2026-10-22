package com.stockflow.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_adjustments")
public class InventoryAdjustment {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "available_delta", nullable = false)
    private long availableDelta;

    @Column(name = "reserved_delta", nullable = false)
    private long reservedDelta;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InventoryAdjustment() {
    }

    public InventoryAdjustment(
            UUID id,
            UUID storeId,
            UUID productId,
            long availableDelta,
            long reservedDelta,
            String reason,
            UUID actorUserId,
            Instant createdAt) {
        this.id = id;
        this.storeId = storeId;
        this.productId = productId;
        this.availableDelta = availableDelta;
        this.reservedDelta = reservedDelta;
        this.reason = reason;
        this.actorUserId = actorUserId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public long getAvailableDelta() { return availableDelta; }
    public long getReservedDelta() { return reservedDelta; }
    public String getReason() { return reason; }
    public UUID getActorUserId() { return actorUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
