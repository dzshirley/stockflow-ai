package com.stockflow.analysis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessAnalysisValidatorTest {

    private final UUID evidenceId = UUID.randomUUID();
    private final UUID findingId = UUID.randomUUID();
    private final UUID hypothesisId = UUID.randomUUID();

    @Test
    void acceptsEvidenceBackedAndEpistemicallySeparatedReport() {
        assertThatCode(() -> BusinessAnalysisValidator.validate(validReport()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsFindingWithoutTraceableEvidence() {
        var original = validReport();
        var badFinding = new BusinessFinding(
                findingId, BusinessFinding.Kind.VERIFIED_OBSERVATION, "Revenue increased",
                "Revenue increased by 20 USD.", List.of(UUID.randomUUID()), null);
        var report = copy(original, List.of(badFinding), original.recommendations());

        assertThatThrownBy(() -> BusinessAnalysisValidator.validate(report))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existing evidence");
    }

    @Test
    void rejectsCalculationWithoutDefinitionAndUnapprovedRecommendation() {
        var original = validReport();
        var badFinding = new BusinessFinding(
                findingId, BusinessFinding.Kind.DETERMINISTIC_CALCULATION, "Revenue growth",
                "Revenue grew.", List.of(evidenceId), " ");
        var badRecommendation = new Recommendation(
                UUID.randomUUID(), "Review inventory", "Review affected inventory.", "Demand may have changed",
                List.of(findingId), List.of(hypothesisId), List.of(evidenceId),
                BusinessAnalysisModels.Priority.MEDIUM, null, null, List.of(),
                List.of("Overstock"), List.of("Owner review"), false);
        var report = copy(original, List.of(badFinding), List.of(badRecommendation));

        assertThatThrownBy(() -> BusinessAnalysisValidator.validate(report))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reproducible definition");
    }

    private BusinessAnalysisReport validReport() {
        var evidence = new EvidenceReference(
                evidenceId, EvidenceReference.Source.ANALYTICS_TOOL, UUID.randomUUID(), "revenue",
                new BigDecimal("120.00"), "USD", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 22), "America/New_York", Map.of("status", "COMPLETED"),
                Instant.parse("2026-10-22T12:00:00Z"));
        var finding = new BusinessFinding(
                findingId, BusinessFinding.Kind.DETERMINISTIC_CALCULATION, "Revenue increased",
                "Revenue increased by 20.00 USD.", List.of(evidenceId),
                "current_period_revenue - previous_period_revenue");
        var hypothesis = new BusinessHypothesis(
                hypothesisId, "More repeat customers may explain the increase.",
                "Repeat-customer segmentation was not included in the evidence.", List.of(evidenceId), List.of(),
                BusinessHypothesis.Status.UNTESTED, "Compare new and repeat customer revenue.");
        var recommendation = new Recommendation(
                UUID.randomUUID(), "Review repeat-customer mix", "Review customer composition before acting.",
                "Validate the hypothesis before acting.", List.of(findingId), List.of(hypothesisId),
                List.of(evidenceId), BusinessAnalysisModels.Priority.MEDIUM, null, null,
                List.of("Customer identity is reliable"), List.of("Customer identity may be incomplete"),
                List.of("Compare new and repeat customers"), true);
        return new BusinessAnalysisReport(
                UUID.randomUUID(), 1, 1, BusinessAnalysisModels.AnalysisType.REVENUE_INVESTIGATION,
                "Revenue investigation", "Revenue increased by 20.00 USD.", UUID.randomUUID(), UUID.randomUUID(),
                BusinessAnalysisReport.Status.COMPLETED, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 22), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 1),
                "America/New_York", "USD", Instant.parse("2026-10-22T12:00:00Z"),
                List.of(new BusinessMetric(
                        "revenue", "Revenue", new BigDecimal("120.00"), new BigDecimal("100.00"),
                        new BigDecimal("20.00"), new BigDecimal("20.00"), "USD", List.of(evidenceId))),
                List.of(evidence), List.of(finding), List.of(hypothesis), List.of(recommendation), List.of(), List.of(),
                List.of(new AnalysisLimitations(
                        "CUSTOMER_SEGMENTATION_UNAVAILABLE", "Customer segments were not evaluated.",
                        List.of(findingId))),
                new BusinessAnalysisReport.ModelMetadata(
                        "openai", "gpt-4o-mini", "business-analysis-v1", 100, 50, 150,
                        "2026-10-22", new BigDecimal("0.00004500"), 500),
                Instant.parse("2026-10-22T12:00:00Z"), Instant.parse("2026-10-22T12:01:00Z"));
    }

    private BusinessAnalysisReport copy(
            BusinessAnalysisReport source,
            List<BusinessFinding> findings,
            List<Recommendation> recommendations) {
        return new BusinessAnalysisReport(
                source.reportId(), source.schemaVersion(), source.reportVersion(), source.reportType(),
                source.title(), source.summary(), source.storeId(), source.createdByUserId(), source.status(),
                source.periodStart(), source.periodEndExclusive(), source.comparisonPeriodStart(),
                source.comparisonPeriodEndExclusive(), source.timeZone(), source.currency(), source.dataAsOf(),
                source.metrics(), source.evidence(), findings, source.hypotheses(), recommendations,
                source.scenarios(), source.charts(), source.limitations(), source.modelMetadata(),
                source.createdAt(), source.completedAt());
    }
}
