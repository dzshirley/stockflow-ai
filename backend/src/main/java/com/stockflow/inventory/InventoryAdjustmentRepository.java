package com.stockflow.inventory;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {
    Page<InventoryAdjustment> findByStoreIdAndProductIdOrderByCreatedAtDesc(
            UUID storeId, UUID productId, Pageable pageable);
}
