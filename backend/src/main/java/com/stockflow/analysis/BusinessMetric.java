package com.stockflow.analysis;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A report metric whose values originate from deterministic analysis. */
public record BusinessMetric(
        String key,
        String label,
        BigDecimal value,
        BigDecimal comparisonValue,
        BigDecimal absoluteChange,
        BigDecimal changePercent,
        String unit,
        List<UUID> evidenceIds) {

    public BusinessMetric {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
