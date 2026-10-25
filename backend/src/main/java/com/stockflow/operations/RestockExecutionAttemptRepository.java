package com.stockflow.operations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RestockExecutionAttemptRepository extends JpaRepository<RestockExecutionAttempt, UUID> {
    Optional<RestockExecutionAttempt> findByStoreIdAndProposalIdAndCommandId(UUID storeId, UUID proposalId, UUID commandId);
    List<RestockExecutionAttempt> findByStoreIdAndProposalIdOrderByStartedAtDescIdDesc(UUID storeId, UUID proposalId);
    long countByProposalId(UUID proposalId);
}
