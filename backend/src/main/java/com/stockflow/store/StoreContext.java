package com.stockflow.store;

import java.util.UUID;

public record StoreContext(UUID userId, UUID storeId, StoreRole role) {
}
