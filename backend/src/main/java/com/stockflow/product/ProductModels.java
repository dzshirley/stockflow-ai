package com.stockflow.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProductModels {

    private ProductModels() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 80) String sku,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 120) String category,
            @NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal price,
            @NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal unitCost,
            @NotNull @Min(0) Long initialQuantity,
            @NotNull @Min(0) Long reorderPoint) {
    }

    public record UpdateRequest(
            @Size(min = 1, max = 80) String sku,
            @Size(min = 1, max = 200) String name,
            @Size(min = 1, max = 120) String category,
            @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal price,
            @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal unitCost) {
    }

    public record Response(
            UUID id,
            String sku,
            String name,
            String category,
            BigDecimal price,
            BigDecimal unitCost,
            ProductStatus status,
            Instant createdAt,
            Instant updatedAt) {

        public static Response from(Product product) {
            return new Response(
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    product.getCategory(),
                    product.getPrice(),
                    product.getUnitCost(),
                    product.getStatus(),
                    product.getCreatedAt(),
                    product.getUpdatedAt());
        }
    }

    public record PageResponse(List<Response> items, int page, int size, long totalElements, int totalPages) {
    }
}
