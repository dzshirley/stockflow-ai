package com.stockflow.store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreMembershipRepository extends JpaRepository<StoreMembership, UUID> {
    List<StoreMembership> findByUserIdOrderByCreatedAtAsc(UUID userId);
    Optional<StoreMembership> findByUserIdAndStoreId(UUID userId, UUID storeId);
    List<StoreMembership> findByStoreIdOrderByCreatedAtAsc(UUID storeId);
}
