package com.stockflow.operations;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RestockProposalEventRepository extends JpaRepository<RestockProposalEvent, UUID> {
    List<RestockProposalEvent> findByStoreIdAndProposalIdOrderByCreatedAtAscIdAsc(UUID storeId, UUID proposalId);
}
