package com.stockflow.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory")
public class InventoryBalance {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "available_quantity", nullable = false)
    private long availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private long reservedQuantity;

    @Column(name = "reorder_point", nullable = false)
    private long reorderPoint;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryBalance() {
    }

    public InventoryBalance(UUID productId, UUID storeId, long availableQuantity, long reorderPoint, Instant now) {
        this.productId = productId;
        this.storeId = storeId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = 0;
        this.reorderPoint = reorderPoint;
        this.updatedAt = now;
    }

    public UUID getProductId() { return productId; }
    public UUID getStoreId() { return storeId; }
    public long getAvailableQuantity() { return availableQuantity; }
    public long getReservedQuantity() { return reservedQuantity; }
    public long getReorderPoint() { return reorderPoint; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
