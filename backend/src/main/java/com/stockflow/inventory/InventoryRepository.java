package com.stockflow.inventory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<InventoryBalance, UUID> {

    Optional<InventoryBalance> findByProductIdAndStoreId(UUID productId, UUID storeId);

    List<InventoryBalance> findByStoreIdOrderByProductId(UUID storeId);

    @Query("""
            SELECT i FROM InventoryBalance i
            WHERE i.storeId = :storeId AND i.availableQuantity <= i.reorderPoint
            ORDER BY i.availableQuantity ASC, i.productId ASC
            """)
    List<InventoryBalance> findLowStock(@Param("storeId") UUID storeId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE InventoryBalance i
            SET i.availableQuantity = i.availableQuantity + :delta,
                i.version = i.version + 1,
                i.updatedAt = :now
            WHERE i.productId = :productId
              AND i.storeId = :storeId
              AND i.availableQuantity + :delta >= 0
            """)
    int adjustAvailable(
            @Param("storeId") UUID storeId,
            @Param("productId") UUID productId,
            @Param("delta") long delta,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE InventoryBalance i
            SET i.reorderPoint = :reorderPoint,
                i.version = i.version + 1,
                i.updatedAt = :now
            WHERE i.productId = :productId AND i.storeId = :storeId
            """)
    int updateReorderPoint(
            @Param("storeId") UUID storeId,
            @Param("productId") UUID productId,
            @Param("reorderPoint") long reorderPoint,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE InventoryBalance i
            SET i.availableQuantity = i.availableQuantity - :quantity,
                i.reservedQuantity = i.reservedQuantity + :quantity,
                i.version = i.version + 1,
                i.updatedAt = :now
            WHERE i.productId = :productId
              AND i.storeId = :storeId
              AND i.availableQuantity >= :quantity
            """)
    int reserve(
            @Param("storeId") UUID storeId,
            @Param("productId") UUID productId,
            @Param("quantity") long quantity,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE InventoryBalance i
            SET i.availableQuantity = i.availableQuantity + :quantity,
                i.reservedQuantity = i.reservedQuantity - :quantity,
                i.version = i.version + 1,
                i.updatedAt = :now
            WHERE i.productId = :productId
              AND i.storeId = :storeId
              AND i.reservedQuantity >= :quantity
            """)
    int release(
            @Param("storeId") UUID storeId,
            @Param("productId") UUID productId,
            @Param("quantity") long quantity,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE InventoryBalance i
            SET i.reservedQuantity = i.reservedQuantity - :quantity,
                i.version = i.version + 1,
                i.updatedAt = :now
            WHERE i.productId = :productId
              AND i.storeId = :storeId
              AND i.reservedQuantity >= :quantity
            """)
    int consumeReservation(
            @Param("storeId") UUID storeId,
            @Param("productId") UUID productId,
            @Param("quantity") long quantity,
            @Param("now") Instant now);
}
