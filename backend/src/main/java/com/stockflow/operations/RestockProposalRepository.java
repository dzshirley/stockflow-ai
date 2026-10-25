package com.stockflow.operations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RestockProposalRepository extends JpaRepository<RestockProposal, UUID> {
    Optional<RestockProposal> findByIdAndStoreId(UUID id, UUID storeId);
    Optional<RestockProposal> findByIdAndStoreIdAndCreatedByUserId(UUID id, UUID storeId, UUID userId);
    List<RestockProposal> findByStoreIdOrderByCreatedAtDescIdDesc(UUID storeId, Pageable page);
    List<RestockProposal> findByStoreIdAndCreatedByUserIdOrderByCreatedAtDescIdDesc(
            UUID storeId, UUID userId, Pageable page);
    List<RestockProposal> findByStoreIdAndCreatedByUserIdAndCreateIdempotencyKeyOrderByProductId(
            UUID storeId, UUID userId, String key);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM RestockProposal p WHERE p.id = :id AND p.storeId = :storeId")
    Optional<RestockProposal> lockOwned(@Param("id") UUID id, @Param("storeId") UUID storeId);

    @Query("""
            SELECT p FROM RestockProposal p
            WHERE p.status = com.stockflow.operations.RestockProposalStatus.EXECUTING
              AND p.executionLeaseUntil <= :now
            ORDER BY p.executionLeaseUntil, p.id
            """)
    List<RestockProposal> findExpired(@Param("now") Instant now, Pageable page);
}
