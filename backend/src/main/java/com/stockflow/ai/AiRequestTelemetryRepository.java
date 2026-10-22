package com.stockflow.ai;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiRequestTelemetryRepository extends JpaRepository<AiRequestTelemetry, UUID> {
    Optional<AiRequestTelemetry> findByRequestId(UUID requestId);
}
