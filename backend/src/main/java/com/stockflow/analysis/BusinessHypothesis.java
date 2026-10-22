package com.stockflow.analysis;

import java.util.List;
import java.util.UUID;

/** A possible explanation that remains explicitly separate from verified findings. */
public record BusinessHypothesis(
        UUID id,
        String statement,
        String rationale,
        List<UUID> supportingEvidenceIds,
        List<UUID> counterEvidenceIds,
        Status status,
        String validationPlan) {

    public BusinessHypothesis {
        supportingEvidenceIds = supportingEvidenceIds == null ? List.of() : List.copyOf(supportingEvidenceIds);
        counterEvidenceIds = counterEvidenceIds == null ? List.of() : List.copyOf(counterEvidenceIds);
    }

    public enum Status {
        UNTESTED,
        SUPPORTED,
        REFUTED,
        INCONCLUSIVE
    }
}
