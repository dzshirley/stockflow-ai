package com.stockflow.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiToolExecutionRepository extends JpaRepository<AiToolExecution, UUID> {
    List<AiToolExecution> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}
