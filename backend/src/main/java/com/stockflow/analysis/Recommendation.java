package com.stockflow.analysis;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import com.stockflow.analysis.BusinessAnalysisModels.Priority;

/** Proposed action. This contract does not authorize or execute the action. */
public record Recommendation(
        UUID id,
        String title,
        String description,
        String rationale,
        List<UUID> findingIds,
        List<UUID> hypothesisIds,
        List<UUID> evidenceIds,
        Priority priority,
        BigDecimal expectedImpact,
        String expectedImpactUnit,
        List<String> assumptions,
        List<String> risks,
        List<String> nextSteps,
        boolean approvalRequired) {

    public Recommendation {
        findingIds = findingIds == null ? List.of() : List.copyOf(findingIds);
        hypothesisIds = hypothesisIds == null ? List.of() : List.copyOf(hypothesisIds);
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        risks = risks == null ? List.of() : List.copyOf(risks);
        nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
    }
}
