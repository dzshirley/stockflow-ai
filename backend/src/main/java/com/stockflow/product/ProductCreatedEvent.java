package com.stockflow.product;

import java.util.UUID;

public record ProductCreatedEvent(
        UUID storeId, UUID productId, UUID actorUserId, long initialQuantity, long reorderPoint) {
}
