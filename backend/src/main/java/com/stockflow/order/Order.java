package com.stockflow.order;

import com.stockflow.common.error.ApiExceptions;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountTotal;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Order() {
    }

    public Order(
            UUID id,
            UUID storeId,
            BigDecimal subtotal,
            String currencyCode,
            Instant expiresAt,
            UUID createdBy,
            Instant now) {
        this.id = id;
        this.storeId = storeId;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.subtotal = subtotal;
        this.discountTotal = BigDecimal.ZERO.setScale(4);
        this.total = subtotal;
        this.currencyCode = currencyCode;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markPaid(Instant now) {
        requirePending("Only pending orders can be paid");
        status = OrderStatus.PAID;
        paidAt = now;
        updatedAt = now;
    }

    public void markPaymentFailed(Instant now) {
        requirePending("Only pending orders can fail payment");
        status = OrderStatus.PAYMENT_FAILED;
        updatedAt = now;
    }

    public void cancel(Instant now) {
        requirePending("Only pending orders can be cancelled");
        status = OrderStatus.CANCELLED;
        updatedAt = now;
    }

    public void expire(Instant now) {
        requirePending("Only pending orders can expire");
        status = OrderStatus.EXPIRED;
        updatedAt = now;
    }

    private void requirePending(String message) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw ApiExceptions.conflict("INVALID_ORDER_TRANSITION", message);
        }
    }

    public UUID getId() { return id; }
    public UUID getStoreId() { return storeId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public BigDecimal getTotal() { return total; }
    public String getCurrencyCode() { return currencyCode; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPaidAt() { return paidAt; }
    public long getVersion() { return version; }
}
