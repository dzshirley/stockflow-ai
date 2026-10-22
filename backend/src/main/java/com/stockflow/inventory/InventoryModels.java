package com.stockflow.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InventoryModels {

    private InventoryModels() {
    }

    public record AdjustmentRequest(
            @NotNull Long availableDelta,
            @Min(0) Long reorderPoint,
            @Size(max = 300) String reason) {
    }

    public record BalanceResponse(
            UUID productId,
            String sku,
            String productName,
            long availableQuantity,
            long reservedQuantity,
            long reorderPoint,
            boolean lowStock,
            long version,
            Instant updatedAt) {
    }

    public record ListResponse(List<BalanceResponse> items) {
    }

    public record AdjustmentResponse(
            UUID id,
            UUID productId,
            long availableDelta,
            long reservedDelta,
            String reason,
            UUID actorUserId,
            Instant createdAt) {

        static AdjustmentResponse from(InventoryAdjustment adjustment) {
            return new AdjustmentResponse(
                    adjustment.getId(),
                    adjustment.getProductId(),
                    adjustment.getAvailableDelta(),
                    adjustment.getReservedDelta(),
                    adjustment.getReason(),
                    adjustment.getActorUserId(),
                    adjustment.getCreatedAt());
        }
    }

    public record AuditResponse(List<AdjustmentResponse> items, int page, int size, long totalElements) {
    }
}
