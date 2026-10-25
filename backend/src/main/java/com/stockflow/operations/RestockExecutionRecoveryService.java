package com.stockflow.operations;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
class RestockExecutionRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(RestockExecutionRecoveryService.class);
    private final RestockProposalPersistenceService persistence;
    private final MeterRegistry meters;
    RestockExecutionRecoveryService(RestockProposalPersistenceService persistence, MeterRegistry meters) {
        this.persistence = persistence; this.meters = meters;
    }
    @Scheduled(fixedDelayString = "${stockflow.restock.recovery-delay:30s}")
    public int reconcileExpiredExecutions() {
        int count = persistence.reconcileExpired();
        if (count > 0) {
            meters.counter("stockflow.restock.execution.recovered").increment(count);
            log.warn("restock_execution_leases_reconciled count={}", count);
        }
        return count;
    }
}
