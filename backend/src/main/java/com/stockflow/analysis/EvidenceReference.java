package com.stockflow.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Immutable provenance for one metric used by an analysis. */
public record EvidenceReference(
        UUID id,
        Source source,
        UUID toolExecutionId,
        String metricKey,
        BigDecimal value,
        String unit,
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        String timeZone,
        Map<String, String> filters,
        Instant observedAt) {

    public EvidenceReference {
        filters = filters == null ? Map.of() : Map.copyOf(filters);
    }

    public enum Source {
        ANALYTICS_TOOL,
        ORDER_SNAPSHOT,
        INVENTORY_SNAPSHOT
    }
}
