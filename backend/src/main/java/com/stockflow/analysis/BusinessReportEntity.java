package com.stockflow.analysis;

import com.stockflow.analysis.BusinessAnalysisModels.AnalysisType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "business_analysis_reports")
class BusinessReportEntity {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 40)
    private AnalysisType analysisType;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BusinessAnalysisReport.Status status;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "report_version", nullable = false)
    private int reportVersion;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end_exclusive", nullable = false)
    private LocalDate periodEndExclusive;

    @Column(name = "comparison_period_start", nullable = false)
    private LocalDate comparisonPeriodStart;

    @Column(name = "comparison_period_end_exclusive", nullable = false)
    private LocalDate comparisonPeriodEndExclusive;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "data_as_of")
    private Instant dataAsOf;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_document", columnDefinition = "jsonb")
    private String reportDocument;

    @Column(name = "model_provider", length = 40)
    private String modelProvider;

    @Column(name = "model_name", length = 160)
    private String modelName;

    @Column(name = "prompt_version", length = 80)
    private String promptVersion;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "pricing_version", length = 40)
    private String pricingVersion;

    @Column(name = "estimated_cost_usd", precision = 19, scale = 8)
    private BigDecimal estimatedCostUsd;

    @Column(name = "model_latency_ms")
    private Long modelLatencyMs;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "generation_owner")
    private UUID generationOwner;

    @Column(name = "generation_started_at")
    private Instant generationStartedAt;

    @Column(name = "generation_lease_until")
    private Instant generationLeaseUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "retention_until", nullable = false)
    private Instant retentionUntil;

    @Version
    @Column(nullable = false)
    private long version;

    protected BusinessReportEntity() {
    }

    BusinessReportEntity(
            UUID id,
            UUID storeId,
            UUID createdByUserId,
            String idempotencyKey,
            String requestHash,
            AnalysisType analysisType,
            String title,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            LocalDate comparisonPeriodStart,
            LocalDate comparisonPeriodEndExclusive,
            String timeZone,
            Instant now,
            Instant retentionUntil) {
        this.id = id;
        this.storeId = storeId;
        this.createdByUserId = createdByUserId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.analysisType = analysisType;
        this.title = title;
        this.status = BusinessAnalysisReport.Status.PENDING;
        this.schemaVersion = 1;
        this.reportVersion = 1;
        this.periodStart = periodStart;
        this.periodEndExclusive = periodEndExclusive;
        this.comparisonPeriodStart = comparisonPeriodStart;
        this.comparisonPeriodEndExclusive = comparisonPeriodEndExclusive;
        this.timeZone = timeZone;
        this.createdAt = now;
        this.updatedAt = now;
        this.retentionUntil = retentionUntil;
    }

    void markRunning(UUID owner, Instant leaseUntil, Instant now) {
        if (status != BusinessAnalysisReport.Status.PENDING) {
            throw new IllegalStateException("Only pending reports can start");
        }
        status = BusinessAnalysisReport.Status.RUNNING;
        generationOwner = owner;
        generationStartedAt = now;
        generationLeaseUntil = leaseUntil;
        updatedAt = now;
    }

    void complete(
            UUID owner,
            String document,
            BusinessAnalysisReport.ModelMetadata model,
            Instant dataAsOf,
            Instant now) {
        if (status != BusinessAnalysisReport.Status.RUNNING) {
            throw new IllegalStateException("Only running reports can complete");
        }
        if (!owner.equals(generationOwner) || generationLeaseUntil == null || !now.isBefore(generationLeaseUntil)) {
            throw new IllegalStateException("The report generation lease is no longer valid");
        }
        status = BusinessAnalysisReport.Status.COMPLETED;
        reportDocument = document;
        this.dataAsOf = dataAsOf;
        modelProvider = model.provider();
        modelName = model.model();
        promptVersion = model.promptVersion();
        inputTokens = model.inputTokens();
        outputTokens = model.outputTokens();
        totalTokens = model.totalTokens();
        pricingVersion = model.pricingVersion();
        estimatedCostUsd = model.estimatedCostUsd();
        modelLatencyMs = model.latencyMs();
        completedAt = now;
        updatedAt = now;
        failureCode = null;
        clearGenerationLease();
    }

    void fail(UUID owner, String code, Instant now) {
        if (status == BusinessAnalysisReport.Status.COMPLETED) {
            throw new IllegalStateException("A completed report cannot fail");
        }
        if (status == BusinessAnalysisReport.Status.RUNNING && !owner.equals(generationOwner)) {
            throw new IllegalStateException("Only the current generation owner can fail this report");
        }
        status = BusinessAnalysisReport.Status.FAILED;
        failureCode = code;
        updatedAt = now;
        clearGenerationLease();
    }

    private void clearGenerationLease() {
        generationOwner = null;
        generationStartedAt = null;
        generationLeaseUntil = null;
    }

    UUID getId() { return id; }
    UUID getStoreId() { return storeId; }
    UUID getCreatedByUserId() { return createdByUserId; }
    String getIdempotencyKey() { return idempotencyKey; }
    String getRequestHash() { return requestHash; }
    AnalysisType getAnalysisType() { return analysisType; }
    String getTitle() { return title; }
    BusinessAnalysisReport.Status getStatus() { return status; }
    int getSchemaVersion() { return schemaVersion; }
    int getReportVersion() { return reportVersion; }
    LocalDate getPeriodStart() { return periodStart; }
    LocalDate getPeriodEndExclusive() { return periodEndExclusive; }
    LocalDate getComparisonPeriodStart() { return comparisonPeriodStart; }
    LocalDate getComparisonPeriodEndExclusive() { return comparisonPeriodEndExclusive; }
    String getTimeZone() { return timeZone; }
    Instant getDataAsOf() { return dataAsOf; }
    String getReportDocument() { return reportDocument; }
    String getFailureCode() { return failureCode; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
    Instant getCompletedAt() { return completedAt; }
    Instant getRetentionUntil() { return retentionUntil; }
    UUID getGenerationOwner() { return generationOwner; }
    Instant getGenerationStartedAt() { return generationStartedAt; }
    Instant getGenerationLeaseUntil() { return generationLeaseUntil; }
}
