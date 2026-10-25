package com.stockflow.analysis;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
class BusinessReportRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(BusinessReportRecoveryService.class);

    private final BusinessReportPersistenceService persistence;
    private final MeterRegistry meters;

    BusinessReportRecoveryService(BusinessReportPersistenceService persistence, MeterRegistry meters) {
        this.persistence = persistence;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${stockflow.business-analysis.recovery-delay:PT30S}")
    public int reconcileExpiredReports() {
        int recovered = persistence.reconcileExpired();
        if (recovered > 0) {
            meters.counter("stockflow.business_analysis.recovery.reports", "outcome", "expired")
                    .increment(recovered);
            log.warn("business_analysis_reports_reconciled count={} code=REPORT_GENERATION_EXPIRED", recovered);
        }
        return recovered;
    }
}
