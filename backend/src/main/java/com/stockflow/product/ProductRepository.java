package com.stockflow.product;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndStoreId(UUID id, UUID storeId);

    boolean existsByStoreIdAndSku(UUID storeId, String sku);

    @Query("""
            SELECT p FROM Product p
            WHERE p.storeId = :storeId
              AND (:status IS NULL OR p.status = :status)
              AND (:category = '' OR LOWER(p.category) = LOWER(:category))
              AND (:search = ''
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Product> search(
            @Param("storeId") UUID storeId,
            @Param("status") ProductStatus status,
            @Param("category") String category,
            @Param("search") String search,
            Pageable pageable);
}
