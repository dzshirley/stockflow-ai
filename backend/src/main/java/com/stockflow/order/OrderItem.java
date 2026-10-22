package com.stockflow.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "sku_snapshot", nullable = false, length = 80)
    private String skuSnapshot;

    @Column(name = "name_snapshot", nullable = false, length = 200)
    private String nameSnapshot;

    @Column(name = "category_snapshot", nullable = false, length = 120)
    private String categorySnapshot;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "unit_cost_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCostSnapshot;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    protected OrderItem() {
    }

    public OrderItem(
            UUID id,
            UUID orderId,
            UUID productId,
            String skuSnapshot,
            String nameSnapshot,
            String categorySnapshot,
            long quantity,
            BigDecimal unitPrice,
            BigDecimal unitCostSnapshot) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.skuSnapshot = skuSnapshot;
        this.nameSnapshot = nameSnapshot;
        this.categorySnapshot = categorySnapshot;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.unitCostSnapshot = unitCostSnapshot;
        this.discountAmount = BigDecimal.ZERO.setScale(4);
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getProductId() { return productId; }
    public String getSkuSnapshot() { return skuSnapshot; }
    public String getNameSnapshot() { return nameSnapshot; }
    public String getCategorySnapshot() { return categorySnapshot; }
    public long getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getUnitCostSnapshot() { return unitCostSnapshot; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
}
