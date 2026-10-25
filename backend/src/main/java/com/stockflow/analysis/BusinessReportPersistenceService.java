package com.stockflow.analysis;

import com.stockflow.analysis.BusinessAnalysisModels.AnalysisType;
import com.stockflow.analysis.BusinessAnalysisModels.ReportList;
import com.stockflow.analysis.BusinessAnalysisModels.ReportSummary;
import com.stockflow.common.error.ApiExceptions;
import com.stockflow.store.StoreContext;
import com.stockflow.store.StoreRole;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class BusinessReportPersistenceService {

    private final BusinessReportRepository reports;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final BusinessAnalysisConfiguration.Properties properties;

    BusinessReportPersistenceService(
            BusinessReportRepository reports,
            JdbcTemplate jdbc,
            Clock clock,
            BusinessAnalysisConfiguration.Properties properties) {
        this.reports = reports;
        this.jdbc = jdbc;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    StartResult begin(
            StoreContext context,
            String idempotencyKey,
            String requestHash,
            AnalysisType type,
            String title,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            LocalDate comparisonStart,
            LocalDate comparisonEndExclusive,
            String timeZone) {
        var now = Instant.now(clock);
        var id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO business_analysis_reports (
                    id, store_id, created_by_user_id, idempotency_key, request_hash, analysis_type, title,
                    status, schema_version, report_version, period_start, period_end_exclusive,
                    comparison_period_start, comparison_period_end_exclusive, time_zone,
                    created_at, updated_at, retention_until, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', 1, 1, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                ON CONFLICT (store_id, created_by_user_id, idempotency_key) DO NOTHING
                """,
                id, context.storeId(), context.userId(), idempotencyKey, requestHash, type.name(), title,
                periodStart, periodEndExclusive, comparisonStart, comparisonEndExclusive, timeZone,
                Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now.plusSeconds(properties.retentionDays() * 86_400L)));
        var gate = jdbc.queryForObject("""
                SELECT id, request_hash
                FROM business_analysis_reports
                WHERE store_id = ? AND created_by_user_id = ? AND idempotency_key = ?
                FOR UPDATE
                """,
                (row, index) -> new StartGate(
                        row.getObject("id", UUID.class), row.getString("request_hash")),
                context.storeId(), context.userId(), idempotencyKey);
        if (gate == null) {
            throw ApiExceptions.conflict(
                    "REPORT_IDEMPOTENCY_CONFLICT", "The report idempotency key could not be acquired");
        }
        if (!gate.requestHash().equals(requestHash)) {
            throw ApiExceptions.conflict(
                    "REPORT_IDEMPOTENCY_CONFLICT",
                    "The idempotency key was already used with a different analysis request");
        }
        return new StartResult(gate.reportId(), inserted == 0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markRunning(UUID reportId, UUID generationOwner) {
        var report = reports.findById(reportId).orElseThrow(() -> ApiExceptions.notFound("Business report"));
        var now = Instant.now(clock);
        report.markRunning(generationOwner, now.plus(properties.generationLease()), now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void complete(
            UUID reportId,
            UUID generationOwner,
            String document,
            BusinessAnalysisReport.ModelMetadata model,
            Instant dataAsOf,
            Instant completedAt) {
        var report = reports.findById(reportId).orElseThrow(() -> ApiExceptions.notFound("Business report"));
        try {
            report.complete(generationOwner, document, model, dataAsOf, completedAt);
        } catch (IllegalStateException exception) {
            throw ApiExceptions.conflict("REPORT_GENERATION_STALE", exception.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void fail(UUID reportId, UUID generationOwner, String code) {
        reports.findById(reportId).ifPresent(report -> {
            if (report.getStatus() == BusinessAnalysisReport.Status.PENDING
                    || report.getStatus() == BusinessAnalysisReport.Status.RUNNING) {
                try {
                    report.fail(generationOwner, code, Instant.now(clock));
                } catch (IllegalStateException ignored) {
                    // Recovery or another valid owner won the lifecycle race.
                }
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int reconcileExpired() {
        var now = Instant.now(clock);
        var pendingCutoff = now.minus(properties.generationLease());
        return jdbc.update("""
                WITH expired AS (
                    SELECT id
                    FROM business_analysis_reports
                    WHERE (status = 'PENDING' AND updated_at <= ?)
                       OR (status = 'RUNNING' AND generation_lease_until <= ?)
                    ORDER BY updated_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE business_analysis_reports report
                SET status = 'FAILED',
                    failure_code = 'REPORT_GENERATION_EXPIRED',
                    generation_owner = NULL,
                    generation_started_at = NULL,
                    generation_lease_until = NULL,
                    updated_at = ?,
                    version = version + 1
                FROM expired
                WHERE report.id = expired.id
                  AND ((report.status = 'PENDING' AND report.updated_at <= ?)
                    OR (report.status = 'RUNNING' AND report.generation_lease_until <= ?))
                """,
                Timestamp.from(pendingCutoff), Timestamp.from(now), properties.recoveryBatchSize(),
                Timestamp.from(now), Timestamp.from(pendingCutoff), Timestamp.from(now));
    }

    @Transactional(readOnly = true)
    BusinessReportEntity get(StoreContext context, UUID reportId) {
        return (context.role() == StoreRole.ADMIN
                ? reports.findByIdAndStoreId(reportId, context.storeId())
                : reports.findByIdAndStoreIdAndCreatedByUserId(reportId, context.storeId(), context.userId()))
                .orElseThrow(() -> ApiExceptions.notFound("Business report"));
    }

    @Transactional(readOnly = true)
    ReportList list(
            StoreContext context,
            BusinessAnalysisReport.Status status,
            AnalysisType type,
            int limit) {
        var page = PageRequest.of(0, limit);
        var values = context.role() == StoreRole.ADMIN
                ? adminPage(context, status, type, page)
                : staffPage(context, status, type, page);
        return new ReportList(values.stream().map(this::summary).toList());
    }

    ReportSummary summary(BusinessReportEntity report) {
        return new ReportSummary(
                report.getId(), report.getAnalysisType(), report.getTitle(), report.getStatus(),
                report.getPeriodStart(), report.getPeriodEndExclusive(), report.getDataAsOf(),
                report.getCreatedAt(), report.getCompletedAt(), report.getFailureCode());
    }

    private org.springframework.data.domain.Page<BusinessReportEntity> adminPage(
            StoreContext context,
            BusinessAnalysisReport.Status status,
            AnalysisType type,
            PageRequest page) {
        if (status != null && type != null) {
            return reports.findByStoreIdAndStatusAndAnalysisTypeOrderByCreatedAtDescIdDesc(
                    context.storeId(), status, type, page);
        }
        if (status != null) return reports.findByStoreIdAndStatusOrderByCreatedAtDescIdDesc(context.storeId(), status, page);
        if (type != null) return reports.findByStoreIdAndAnalysisTypeOrderByCreatedAtDescIdDesc(context.storeId(), type, page);
        return reports.findByStoreIdOrderByCreatedAtDescIdDesc(context.storeId(), page);
    }

    private org.springframework.data.domain.Page<BusinessReportEntity> staffPage(
            StoreContext context,
            BusinessAnalysisReport.Status status,
            AnalysisType type,
            PageRequest page) {
        if (status != null && type != null) {
            return reports.findByStoreIdAndCreatedByUserIdAndStatusAndAnalysisTypeOrderByCreatedAtDescIdDesc(
                    context.storeId(), context.userId(), status, type, page);
        }
        if (status != null) {
            return reports.findByStoreIdAndCreatedByUserIdAndStatusOrderByCreatedAtDescIdDesc(
                    context.storeId(), context.userId(), status, page);
        }
        if (type != null) {
            return reports.findByStoreIdAndCreatedByUserIdAndAnalysisTypeOrderByCreatedAtDescIdDesc(
                    context.storeId(), context.userId(), type, page);
        }
        return reports.findByStoreIdAndCreatedByUserIdOrderByCreatedAtDescIdDesc(
                context.storeId(), context.userId(), page);
    }

    record StartResult(UUID reportId, boolean replayed) {
    }

    private record StartGate(UUID reportId, String requestHash) {
    }
}
