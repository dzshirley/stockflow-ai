package com.stockflow.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Immutable, versioned Phase 6 report document persisted as JSONB. */
public record BusinessAnalysisReport(
        UUID reportId,
        int schemaVersion,
        int reportVersion,
        BusinessAnalysisModels.AnalysisType reportType,
        String title,
        String summary,
        UUID storeId,
        UUID createdByUserId,
        Status status,
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        LocalDate comparisonPeriodStart,
        LocalDate comparisonPeriodEndExclusive,
        String timeZone,
        String currency,
        Instant dataAsOf,
        List<BusinessMetric> metrics,
        List<EvidenceReference> evidence,
        List<BusinessFinding> findings,
        List<BusinessHypothesis> hypotheses,
        List<Recommendation> recommendations,
        List<BusinessScenario> scenarios,
        List<BusinessChart> charts,
        List<AnalysisLimitations> limitations,
        ModelMetadata modelMetadata,
        Instant createdAt,
        Instant completedAt) {

    public BusinessAnalysisReport {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        findings = findings == null ? List.of() : List.copyOf(findings);
        hypotheses = hypotheses == null ? List.of() : List.copyOf(hypotheses);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
        charts = charts == null ? List.of() : List.copyOf(charts);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }

    public enum Status {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public record ModelMetadata(
            String provider,
            String model,
            String promptVersion,
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens,
            String pricingVersion,
            BigDecimal estimatedCostUsd,
            long latencyMs) {
    }
}
