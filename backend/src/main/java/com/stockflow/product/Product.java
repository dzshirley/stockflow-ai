package com.stockflow.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(nullable = false, length = 80)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 120)
    private String category;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(
            UUID id,
            UUID storeId,
            String sku,
            String name,
            String category,
            BigDecimal price,
            BigDecimal unitCost,
            Instant now) {
        this.id = id;
        this.storeId = storeId;
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.price = price;
        this.unitCost = unitCost;
        this.status = ProductStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String sku, String name, String category, BigDecimal price, BigDecimal unitCost, Instant now) {
        if (sku != null) this.sku = sku;
        if (name != null) this.name = name;
        if (category != null) this.category = category;
        if (price != null) this.price = price;
        if (unitCost != null) this.unitCost = unitCost;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        this.status = ProductStatus.ARCHIVED;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getStoreId() { return storeId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getUnitCost() { return unitCost; }
    public ProductStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
