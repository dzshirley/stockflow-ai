package com.stockflow.analysis;

import com.stockflow.analysis.BusinessAnalysisModels.ScenarioType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Deterministic what-if output. It is a modeled scenario, not a forecast. */
public record BusinessScenario(
        UUID id,
        ScenarioType type,
        BigDecimal changePercent,
        String statement,
        BigDecimal baselineValue,
        BigDecimal modeledValue,
        String unit,
        BigDecimal baselineGrossProfit,
        BigDecimal modeledGrossProfit,
        BigDecimal grossProfitChange,
        List<UUID> evidenceIds,
        List<String> assumptions,
        List<String> limitations) {

    public BusinessScenario {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}
