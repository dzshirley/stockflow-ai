package com.stockflow.analysis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Validates referential integrity and epistemic separation in a report document. */
public final class BusinessAnalysisValidator {

    private BusinessAnalysisValidator() {
    }

    public static void validate(BusinessAnalysisReport report) {
        require(report != null, "report is required");
        require(report.reportId() != null && report.storeId() != null && report.createdByUserId() != null,
                "report and owner identifiers are required");
        require(report.schemaVersion() > 0 && report.reportVersion() > 0,
                "schema and report versions must be positive");
        require(report.status() != null, "status is required");
        require(report.reportType() != null && hasText(report.title()) && hasText(report.summary()),
                "report type, title, and summary are required");
        require(report.periodStart() != null && report.periodEndExclusive() != null
                        && report.periodStart().isBefore(report.periodEndExclusive()),
                "reporting period must be a non-empty half-open range");
        require(report.comparisonPeriodStart() != null && report.comparisonPeriodEndExclusive() != null
                        && report.comparisonPeriodStart().isBefore(report.comparisonPeriodEndExclusive()),
                "comparison period must be a non-empty half-open range");
        require(hasText(report.timeZone()) && hasText(report.currency())
                        && report.dataAsOf() != null && report.createdAt() != null,
                "time zone, currency, data freshness, and creation time are required");
        if (report.status() == BusinessAnalysisReport.Status.COMPLETED) {
            require(report.completedAt() != null, "completed reports require a completion time");
        }
        var model = report.modelMetadata();
        require(model != null && hasText(model.provider()) && hasText(model.model())
                        && hasText(model.promptVersion()) && model.latencyMs() >= 0,
                "model provider, model, prompt version, and nonnegative latency are required");
        boolean noUsage = model.inputTokens() == null && model.outputTokens() == null && model.totalTokens() == null;
        boolean validUsage = model.inputTokens() != null && model.outputTokens() != null && model.totalTokens() != null
                && model.inputTokens() >= 0 && model.outputTokens() >= 0
                && model.totalTokens() >= model.inputTokens() + model.outputTokens();
        require(noUsage || validUsage, "model token usage must be entirely absent or internally consistent");
        require(model.estimatedCostUsd() == null || model.estimatedCostUsd().signum() >= 0,
                "estimated model cost cannot be negative");

        Set<UUID> evidenceIds = uniqueIds(report.evidence().stream().map(EvidenceReference::id).toList(), "evidence");
        Set<UUID> findingIds = uniqueIds(report.findings().stream().map(BusinessFinding::id).toList(), "finding");
        Set<UUID> hypothesisIds = uniqueIds(report.hypotheses().stream().map(BusinessHypothesis::id).toList(), "hypothesis");
        uniqueIds(report.recommendations().stream().map(Recommendation::id).toList(), "recommendation");
        uniqueIds(report.scenarios().stream().map(BusinessScenario::id).toList(), "scenario");

        for (BusinessMetric item : report.metrics()) {
            require(hasText(item.key()) && hasText(item.label()) && item.value() != null && hasText(item.unit()),
                    "metrics require key, label, value, and unit");
            require(!item.evidenceIds().isEmpty() && evidenceIds.containsAll(item.evidenceIds()),
                    "metrics must reference existing evidence");
        }

        for (EvidenceReference item : report.evidence()) {
            require(item.source() != null && hasText(item.metricKey()) && item.value() != null && hasText(item.unit()),
                    "evidence requires source, metric, value, and unit");
            require(item.periodStart() != null && item.periodEndExclusive() != null
                            && item.periodStart().isBefore(item.periodEndExclusive()),
                    "evidence period must be a non-empty half-open range");
            require(hasText(item.timeZone()) && item.observedAt() != null,
                    "evidence time zone and observation time are required");
            if (item.source() == EvidenceReference.Source.ANALYTICS_TOOL) {
                require(item.toolExecutionId() != null, "analytics-tool evidence requires a tool execution id");
            }
        }
        for (BusinessFinding item : report.findings()) {
            require(item.kind() != null && hasText(item.title()) && hasText(item.statement()),
                    "findings require kind, title, and statement");
            require(!item.evidenceIds().isEmpty() && evidenceIds.containsAll(item.evidenceIds()),
                    "every finding must reference existing evidence");
            if (item.kind() == BusinessFinding.Kind.DETERMINISTIC_CALCULATION) {
                require(hasText(item.calculationDefinition()),
                        "deterministic calculations require a reproducible definition");
            }
        }
        for (BusinessHypothesis item : report.hypotheses()) {
            require(hasText(item.statement()) && hasText(item.rationale()) && item.status() != null
                            && hasText(item.validationPlan()),
                    "hypotheses require rationale, status, and a validation plan");
            require(evidenceIds.containsAll(item.supportingEvidenceIds())
                            && evidenceIds.containsAll(item.counterEvidenceIds()),
                    "hypotheses may reference only existing evidence");
        }
        for (Recommendation item : report.recommendations()) {
            require(hasText(item.title()) && hasText(item.description()) && hasText(item.rationale())
                            && item.priority() != null && !item.nextSteps().isEmpty(),
                    "recommendations require title, description, rationale, priority, and next steps");
            require(findingIds.containsAll(item.findingIds()) && hypothesisIds.containsAll(item.hypothesisIds()),
                    "recommendations may reference only existing findings and hypotheses");
            require(!item.evidenceIds().isEmpty() && evidenceIds.containsAll(item.evidenceIds()),
                    "recommendations must reference existing evidence");
            require(item.expectedImpact() == null || hasText(item.expectedImpactUnit()),
                    "estimated recommendation impact requires a unit");
            require(item.approvalRequired(), "recommendations must require human approval");
        }
        for (BusinessScenario item : report.scenarios()) {
            require(item.type() != null && item.changePercent() != null && hasText(item.statement())
                            && hasText(item.unit()),
                    "scenarios require type, assumption, and unit");
            require(item.modeledValue() != null || !item.limitations().isEmpty(),
                    "unavailable scenario results require an explicit limitation");
            require(!item.evidenceIds().isEmpty() && evidenceIds.containsAll(item.evidenceIds()),
                    "scenarios must reference existing evidence");
        }
        for (AnalysisLimitations item : report.limitations()) {
            require(hasText(item.code()) && hasText(item.description())
                            && findingIds.containsAll(item.affectedFindingIds()),
                    "limitations require a code, description, and valid finding references");
        }
    }

    private static Set<UUID> uniqueIds(List<UUID> ids, String label) {
        require(ids.stream().noneMatch(java.util.Objects::isNull), label + " ids are required");
        Set<UUID> unique = new HashSet<>(ids);
        require(unique.size() == ids.size(), label + " ids must be unique");
        return unique;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
