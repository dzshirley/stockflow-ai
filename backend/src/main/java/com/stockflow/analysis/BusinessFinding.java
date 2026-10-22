package com.stockflow.analysis;

import java.util.List;
import java.util.UUID;

/** A verified observation or reproducible deterministic calculation. */
public record BusinessFinding(
        UUID id,
        Kind kind,
        String title,
        String statement,
        List<UUID> evidenceIds,
        String calculationDefinition) {

    public BusinessFinding {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }

    public enum Kind {
        VERIFIED_OBSERVATION,
        DETERMINISTIC_CALCULATION
    }
}
