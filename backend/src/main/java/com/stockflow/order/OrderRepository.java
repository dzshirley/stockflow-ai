package com.stockflow.order;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndStoreId(UUID id, UUID storeId);

    Page<Order> findByStoreIdOrderByCreatedAtDesc(UUID storeId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.storeId = :storeId")
    Optional<Order> findForUpdate(@Param("id") UUID id, @Param("storeId") UUID storeId);

    List<Order> findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            OrderStatus status, Instant expiresAt);
}
