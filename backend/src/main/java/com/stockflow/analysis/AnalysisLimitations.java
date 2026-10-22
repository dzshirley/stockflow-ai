package com.stockflow.analysis;

import java.util.List;
import java.util.UUID;

/** A known data, method, freshness, or interpretation constraint. */
public record AnalysisLimitations(
        String code,
        String description,
        List<UUID> affectedFindingIds) {

    public AnalysisLimitations {
        affectedFindingIds = affectedFindingIds == null ? List.of() : List.copyOf(affectedFindingIds);
    }
}
