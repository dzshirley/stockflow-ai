package com.stockflow.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderModels {

    private OrderModels() {
    }

    public record LineRequest(@NotNull UUID productId, @Min(1) long quantity) {
    }

    public record CreateRequest(@NotEmpty List<@Valid LineRequest> items) {
    }

    public enum PaymentOutcome {
        APPROVED,
        DECLINED
    }

    public record PaymentRequest(@NotNull PaymentOutcome outcome) {
    }

    public record LineResponse(
            UUID productId,
            String sku,
            String name,
            long quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount) {

        static LineResponse from(OrderItem item) {
            return new LineResponse(
                    item.getProductId(),
                    item.getSkuSnapshot(),
                    item.getNameSnapshot(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getDiscountAmount());
        }
    }

    public record Response(
            UUID id,
            OrderStatus status,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal total,
            String currencyCode,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt,
            List<LineResponse> items) {
    }

    public record PageResponse(List<Response> items, int page, int size, long totalElements, int totalPages) {
    }
}
