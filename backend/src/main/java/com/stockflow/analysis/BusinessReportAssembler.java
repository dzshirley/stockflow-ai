package com.stockflow.analysis;

import com.stockflow.ai.BusinessAnalysisAiService;
import com.stockflow.ai.BusinessAnalysisAiService.HypothesisCode;
import com.stockflow.ai.BusinessAnalysisAiService.RecommendationCode;
import com.stockflow.analysis.BusinessAnalysisModels.AnalysisBundle;
import com.stockflow.analysis.BusinessAnalysisModels.AnalysisType;
import com.stockflow.analysis.BusinessAnalysisModels.Priority;
import com.stockflow.store.StoreContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class BusinessReportAssembler {

    private final BusinessAnalysisConfiguration.Properties properties;

    BusinessReportAssembler(BusinessAnalysisConfiguration.Properties properties) {
        this.properties = properties;
    }

    Map<String, Object> synthesisBundle(AnalysisType type, AnalysisBundle bundle) {
        var revenue = bundle.revenue();
        var values = new LinkedHashMap<String, Object>();
        values.put("analysisType", type.name());
        values.put("revenueDirection", revenue.revenueChange().absoluteChange().signum());
        values.put("revenueChangePercent", revenue.revenueChange().percentageChange());
        values.put("productResidual", revenue.productResidual());
        values.put("categoryResidual", revenue.categoryResidual());
        values.put("newProductCount", bundle.priceVolume().items().stream()
                .filter(item -> item.lifecycle() == BusinessAnalysisModels.ProductLifecycle.NEW).count());
        values.put("discontinuedProductCount", bundle.priceVolume().items().stream()
                .filter(item -> item.lifecycle() == BusinessAnalysisModels.ProductLifecycle.DISCONTINUED).count());
        values.put("outOfStockCount", bundle.inventoryDiagnostics().stream()
                .filter(item -> item.riskLevel().equals("OUT_OF_STOCK")).count());
        values.put("lowStockCount", bundle.inventoryDiagnostics().stream()
                .filter(item -> item.riskLevel().equals("LOW_STOCK") || item.riskLevel().equals("AT_RISK")).count());
        values.put("excessInventoryCount", bundle.inventoryDiagnostics().stream()
                .filter(BusinessAnalysisModels.InventoryDiagnostic::excessInventory).count());
        return values;
    }

    BusinessAnalysisReport assemble(
            UUID reportId,
            StoreContext context,
            AnalysisType type,
            String title,
            AnalysisBundle bundle,
            BusinessAnalysisAiService.Synthesis synthesis,
            Instant createdAt,
            Instant completedAt) {
        var evidence = new ArrayList<EvidenceReference>();
        var currentRevenueEvidence = evidence(
                "revenue.current", bundle.revenue().current().revenue(), bundle.revenue().currency(),
                bundle.revenue().currentPeriod(), completedAt, Map.of("orderStatus", "PAID"));
        var previousRevenueEvidence = evidence(
                "revenue.previous", bundle.revenue().previous().revenue(), bundle.revenue().currency(),
                bundle.revenue().comparisonPeriod(), completedAt, Map.of("orderStatus", "PAID"));
        evidence.add(previousRevenueEvidence);
        evidence.add(currentRevenueEvidence);
        var currentOrdersEvidence = evidence(
                "orders.current", BigDecimal.valueOf(bundle.revenue().current().orders()), "COUNT",
                bundle.revenue().currentPeriod(), completedAt, Map.of("orderStatus", "PAID"));
        var previousOrdersEvidence = evidence(
                "orders.previous", BigDecimal.valueOf(bundle.revenue().previous().orders()), "COUNT",
                bundle.revenue().comparisonPeriod(), completedAt, Map.of("orderStatus", "PAID"));
        evidence.add(previousOrdersEvidence);
        evidence.add(currentOrdersEvidence);
        var currentUnitsEvidence = evidence(
                "units.current", BigDecimal.valueOf(bundle.revenue().current().unitsSold()), "COUNT",
                bundle.revenue().currentPeriod(), completedAt, Map.of("orderStatus", "PAID"));
        var previousUnitsEvidence = evidence(
                "units.previous", BigDecimal.valueOf(bundle.revenue().previous().unitsSold()), "COUNT",
                bundle.revenue().comparisonPeriod(), completedAt, Map.of("orderStatus", "PAID"));
        evidence.add(previousUnitsEvidence);
        evidence.add(currentUnitsEvidence);

        var metrics = new ArrayList<BusinessMetric>();
        metrics.add(metric("revenue", "Revenue", bundle.revenue().revenueChange(),
                bundle.revenue().currency(), previousRevenueEvidence.id(), currentRevenueEvidence.id()));
        metrics.add(metric("orders", "Paid orders", bundle.revenue().orderCountChange(),
                "COUNT", previousOrdersEvidence.id(), currentOrdersEvidence.id()));
        metrics.add(metric("unitsSold", "Units sold", bundle.revenue().unitsSoldChange(),
                "COUNT", previousUnitsEvidence.id(), currentUnitsEvidence.id()));
        if (bundle.revenue().current().averageOrderValue() != null) {
            var aovEvidence = evidence(
                    "averageOrderValue.current", bundle.revenue().current().averageOrderValue(),
                    bundle.revenue().currency(), bundle.revenue().currentPeriod(), completedAt,
                    Map.of("orderStatus", "PAID"));
            evidence.add(aovEvidence);
            metrics.add(new BusinessMetric(
                    "averageOrderValue", "Average order value", bundle.revenue().current().averageOrderValue(),
                    bundle.revenue().previous().averageOrderValue(),
                    bundle.revenue().averageOrderValueChange().absoluteChange(),
                    bundle.revenue().averageOrderValueChange().percentageChange(), bundle.revenue().currency(),
                    List.of(aovEvidence.id())));
        }
        var previousGrossProfitEvidence = evidence(
                "grossProfit.previous", bundle.revenue().previous().grossProfit(), bundle.revenue().currency(),
                bundle.revenue().comparisonPeriod(), completedAt, Map.of("orderStatus", "PAID"));
        var currentGrossProfitEvidence = evidence(
                "grossProfit.current", bundle.revenue().current().grossProfit(), bundle.revenue().currency(),
                bundle.revenue().currentPeriod(), completedAt, Map.of("orderStatus", "PAID"));
        evidence.add(previousGrossProfitEvidence);
        evidence.add(currentGrossProfitEvidence);
        metrics.add(metric(
                "grossProfit", "Gross profit",
                BusinessAnalysisCalculations.change(
                        bundle.revenue().previous().grossProfit(), bundle.revenue().current().grossProfit()),
                bundle.revenue().currency(), previousGrossProfitEvidence.id(), currentGrossProfitEvidence.id()));

        var findings = new ArrayList<BusinessFinding>();
        var revenueFinding = new BusinessFinding(
                UUID.randomUUID(), BusinessFinding.Kind.VERIFIED_OBSERVATION, "Revenue changed",
                revenueStatement(bundle), List.of(previousRevenueEvidence.id(), currentRevenueEvidence.id()), null);
        findings.add(revenueFinding);

        int contributionLimit = Math.min(properties.maxContributionRows(), bundle.revenue().productContributions().size());
        var productPoints = new ArrayList<BusinessChart.Point>();
        for (var contribution : bundle.revenue().productContributions().subList(0, contributionLimit)) {
            var itemEvidence = evidence(
                    "product.revenueContribution." + contribution.dimensionKey(), contribution.contribution(),
                    bundle.revenue().currency(), bundle.revenue().currentPeriod(), completedAt,
                    Map.of("comparisonStart", bundle.revenue().comparisonPeriod().startDate().toString(),
                            "comparisonEndExclusive", bundle.revenue().comparisonPeriod().endDateExclusive().toString()));
            evidence.add(itemEvidence);
            productPoints.add(new BusinessChart.Point(
                    contribution.dimensionKey(), contribution.label(), contribution.contribution()));
        }
        int categoryLimit = Math.min(properties.maxContributionRows(), bundle.revenue().categoryContributions().size());
        var categoryPoints = new ArrayList<BusinessChart.Point>();
        for (var contribution : bundle.revenue().categoryContributions().subList(0, categoryLimit)) {
            var itemEvidence = evidence(
                    "category.revenueContribution." + contribution.dimensionKey(), contribution.contribution(),
                    bundle.revenue().currency(), bundle.revenue().currentPeriod(), completedAt,
                    Map.of("comparisonStart", bundle.revenue().comparisonPeriod().startDate().toString(),
                            "comparisonEndExclusive", bundle.revenue().comparisonPeriod().endDateExclusive().toString()));
            evidence.add(itemEvidence);
            categoryPoints.add(new BusinessChart.Point(
                    contribution.dimensionKey(), contribution.label(), contribution.contribution()));
        }
        if (!productPoints.isEmpty()) {
            var top = productPoints.getFirst();
            var topEvidence = evidence.stream()
                    .filter(item -> item.metricKey().equals("product.revenueContribution." + top.key()))
                    .findFirst().orElseThrow();
            findings.add(new BusinessFinding(
                    UUID.randomUUID(), BusinessFinding.Kind.DETERMINISTIC_CALCULATION,
                    "Largest product contribution",
                    top.label() + " contributed " + top.value().toPlainString() + " "
                            + bundle.revenue().currency() + " to the period-over-period revenue change.",
                    List.of(topEvidence.id()), "current_product_revenue - previous_product_revenue"));
        }

        var priceVolumePoints = addPriceVolumeEvidence(bundle, evidence, metrics, findings, completedAt);
        var inventoryEvidence = inventoryEvidence(bundle, evidence, completedAt);
        var inventoryPoints = addInventoryFindings(bundle, evidence, findings);
        var hypotheses = hypotheses(
                synthesis.hypothesisCodes(), inventoryEvidence.risk(), previousRevenueEvidence, currentRevenueEvidence);
        var recommendations = recommendations(
                synthesis.recommendationCodes(), bundle, findings, hypotheses,
                currentRevenueEvidence, inventoryEvidence);
        var scenarios = scenarios(bundle, currentRevenueEvidence);
        var charts = new ArrayList<BusinessChart>();
        charts.add(new BusinessChart("product-contributions", BusinessChart.ChartType.WATERFALL,
                        "Product contribution to revenue change", "Product", "Contribution",
                        bundle.revenue().currency(), productPoints));
        charts.add(new BusinessChart("category-contributions", BusinessChart.ChartType.BAR,
                        "Category contribution to revenue change", "Category", "Contribution",
                        bundle.revenue().currency(), categoryPoints));
        if (!priceVolumePoints.isEmpty()) {
            charts.add(new BusinessChart("price-volume-decomposition", BusinessChart.ChartType.WATERFALL,
                    "Price, volume, and lifecycle effects", "Effect", "Revenue change",
                    bundle.revenue().currency(), priceVolumePoints));
        }
        if (!inventoryPoints.isEmpty()) {
            charts.add(new BusinessChart("inventory-days-of-cover", BusinessChart.ChartType.BAR,
                    "Current inventory days of cover", "Product", "Days", "DAYS", inventoryPoints));
        }
        var limitationFindingIds = findings.stream().map(BusinessFinding::id).toList();
        var limitations = new ArrayList<AnalysisLimitations>();
        for (int i = 0; i < bundle.limitations().size(); i++) {
            limitations.add(new AnalysisLimitations(
                    "ANALYSIS_LIMITATION_" + (i + 1), bundle.limitations().get(i), limitationFindingIds));
        }

        var model = new BusinessAnalysisReport.ModelMetadata(
                synthesis.provider(), synthesis.model(), properties.promptVersion(), synthesis.inputTokens(),
                synthesis.outputTokens(), synthesis.totalTokens(), synthesis.pricingVersion(),
                synthesis.estimatedCostUsd(), synthesis.latencyMs());
        var report = new BusinessAnalysisReport(
                reportId, 1, 1, type, title, revenueStatement(bundle), context.storeId(), context.userId(),
                BusinessAnalysisReport.Status.COMPLETED,
                bundle.revenue().currentPeriod().startDate(), bundle.revenue().currentPeriod().endDateExclusive(),
                bundle.revenue().comparisonPeriod().startDate(),
                bundle.revenue().comparisonPeriod().endDateExclusive(),
                bundle.revenue().currentPeriod().timeZone(), bundle.revenue().currency(), completedAt,
                metrics, evidence, findings, hypotheses, recommendations, scenarios, charts, limitations,
                model, createdAt, completedAt);
        BusinessAnalysisValidator.validate(report);
        return report;
    }

    private List<BusinessChart.Point> addPriceVolumeEvidence(
            AnalysisBundle bundle,
            List<EvidenceReference> evidence,
            List<BusinessMetric> metrics,
            List<BusinessFinding> findings,
            Instant observedAt) {
        var decomposition = bundle.priceVolume();
        var values = new LinkedHashMap<String, BigDecimal>();
        values.put("priceEffect", decomposition.priceEffect());
        values.put("volumeEffect", decomposition.volumeEffect());
        values.put("newProductEffect", decomposition.newProductEffect());
        values.put("discontinuedProductEffect", decomposition.discontinuedProductEffect());
        values.put("residual", decomposition.residual());
        var labels = Map.of(
                "priceEffect", "Price effect",
                "volumeEffect", "Volume effect",
                "newProductEffect", "New-product effect",
                "discontinuedProductEffect", "Discontinued-product effect",
                "residual", "Rounding residual");
        var points = new ArrayList<BusinessChart.Point>();
        var ids = new ArrayList<UUID>();
        for (var entry : values.entrySet()) {
            var item = evidence(
                    "revenueDecomposition." + entry.getKey(), entry.getValue(), bundle.revenue().currency(),
                    bundle.revenue().currentPeriod(), observedAt,
                    Map.of("method", decomposition.method(),
                            "comparisonStart", bundle.revenue().comparisonPeriod().startDate().toString(),
                            "comparisonEndExclusive", bundle.revenue().comparisonPeriod().endDateExclusive().toString()));
            evidence.add(item);
            ids.add(item.id());
            points.add(new BusinessChart.Point(entry.getKey(), labels.get(entry.getKey()), entry.getValue()));
            metrics.add(new BusinessMetric(
                    entry.getKey(), labels.get(entry.getKey()), entry.getValue(), null, entry.getValue(), null,
                    bundle.revenue().currency(), List.of(item.id())));
        }
        findings.add(new BusinessFinding(
                UUID.randomUUID(), BusinessFinding.Kind.DETERMINISTIC_CALCULATION,
                "Revenue change decomposition",
                "Price, volume, and product lifecycle effects reconcile to the paid-order revenue change with a "
                        + decomposition.residual().toPlainString() + " " + bundle.revenue().currency()
                        + " rounding residual.",
                ids, decomposition.method()));
        return points;
    }

    private InventoryEvidence inventoryEvidence(
            AnalysisBundle bundle, List<EvidenceReference> evidence, Instant observedAt) {
        EvidenceReference riskRepresentative = null;
        EvidenceReference excessRepresentative = null;
        int limit = Math.min(properties.maxContributionRows(), bundle.inventoryDiagnostics().size());
        for (var diagnostic : bundle.inventoryDiagnostics().subList(0, limit)) {
            var filters = new LinkedHashMap<String, String>();
            filters.put("riskLevel", diagnostic.riskLevel());
            filters.put("sku", diagnostic.sku());
            filters.put("excessInventory", Boolean.toString(diagnostic.excessInventory()));
            filters.put("reservedQuantity", Long.toString(diagnostic.reservedQuantity()));
            var item = new EvidenceReference(
                    UUID.randomUUID(), EvidenceReference.Source.INVENTORY_SNAPSHOT, null,
                    "inventory.available." + diagnostic.productId(),
                    BigDecimal.valueOf(diagnostic.availableQuantity()), "COUNT",
                    bundle.revenue().currentPeriod().startDate(), bundle.revenue().currentPeriod().endDateExclusive(),
                    bundle.revenue().currentPeriod().timeZone(), filters, observedAt);
            evidence.add(item);
            if (riskRepresentative == null && !diagnostic.riskLevel().equals("HEALTHY")
                    && !diagnostic.riskLevel().equals("NO_SALES")) riskRepresentative = item;
            if (excessRepresentative == null && diagnostic.excessInventory()) excessRepresentative = item;
        }
        return new InventoryEvidence(riskRepresentative, excessRepresentative);
    }

    private List<BusinessChart.Point> addInventoryFindings(
            AnalysisBundle bundle,
            List<EvidenceReference> evidence,
            List<BusinessFinding> findings) {
        var points = new ArrayList<BusinessChart.Point>();
        for (var diagnostic : bundle.inventoryDiagnostics()) {
            if (diagnostic.daysOfCover() != null) {
                points.add(new BusinessChart.Point(
                        diagnostic.productId().toString(), diagnostic.name(), diagnostic.daysOfCover()));
            }
            if (diagnostic.riskLevel().equals("HEALTHY") && !diagnostic.excessInventory()) continue;
            var itemEvidence = evidence.stream()
                    .filter(item -> item.metricKey().equals("inventory.available." + diagnostic.productId()))
                    .findFirst().orElse(null);
            if (itemEvidence == null) continue;
            String condition = diagnostic.excessInventory()
                    ? "is flagged as potential excess inventory"
                    : "has a current inventory risk level of " + diagnostic.riskLevel();
            findings.add(new BusinessFinding(
                    UUID.randomUUID(), BusinessFinding.Kind.VERIFIED_OBSERVATION,
                    "Inventory diagnostic: " + diagnostic.name(),
                    diagnostic.name() + " (" + diagnostic.sku() + ") " + condition
                            + "; available quantity is " + diagnostic.availableQuantity() + ".",
                    List.of(itemEvidence.id()), null));
        }
        return points;
    }

    private List<BusinessHypothesis> hypotheses(
            List<HypothesisCode> codes,
            EvidenceReference inventory,
            EvidenceReference previousRevenue,
            EvidenceReference currentRevenue) {
        var result = new ArrayList<BusinessHypothesis>();
        for (var code : codes) {
            switch (code) {
                case INVENTORY_AVAILABILITY_MAY_CONTRIBUTE -> {
                    if (inventory != null) result.add(new BusinessHypothesis(
                            UUID.randomUUID(), "Limited current inventory may be associated with observed sales performance.",
                            "At least one current inventory item is at risk, but historical stockout duration is unavailable.",
                            List.of(inventory.id(), currentRevenue.id()), List.of(),
                            BusinessHypothesis.Status.UNTESTED,
                            "Capture inventory snapshots and compare stockout intervals with product sales over time."));
                }
                case PRODUCT_MIX_MAY_CONTRIBUTE -> result.add(new BusinessHypothesis(
                        UUID.randomUUID(), "A change in product mix may have influenced the revenue change.",
                        "Product contributions differ between the two reporting periods.",
                        List.of(previousRevenue.id(), currentRevenue.id()), List.of(),
                        BusinessHypothesis.Status.UNTESTED,
                        "Compare product share of units and realized net price across additional periods."));
            }
        }
        return result;
    }

    private List<Recommendation> recommendations(
            List<RecommendationCode> codes,
            AnalysisBundle bundle,
            List<BusinessFinding> findings,
            List<BusinessHypothesis> hypotheses,
            EvidenceReference revenueEvidence,
            InventoryEvidence inventoryEvidence) {
        var result = new ArrayList<Recommendation>();
        for (var code : codes) {
            switch (code) {
                case REVIEW_REVENUE_DRIVERS -> result.add(new Recommendation(
                        UUID.randomUUID(), "Review the largest revenue drivers",
                        "Inspect the products and categories with the largest absolute contributions.",
                        "The contribution ranking deterministically identifies where the reported change accumulated.",
                        findings.stream().map(BusinessFinding::id).toList(), List.of(),
                        List.of(revenueEvidence.id()), revenuePriority(bundle), null, null,
                        List.of("Paid-order snapshots are representative of the selected periods."),
                        List.of("Short periods may amplify one-off orders."),
                        List.of("Review the contribution charts", "Compare affected SKUs with operational context"), true));
                case REVIEW_INVENTORY_RISK -> {
                    if (inventoryEvidence.risk() != null) {
                        result.add(new Recommendation(
                                UUID.randomUUID(), "Review inventory risk for affected products",
                                "Check replenishment timing and current availability for at-risk SKUs.",
                                "Current inventory risk is verified, while any effect on historical sales remains a hypothesis.",
                                findings.stream().map(BusinessFinding::id).toList(),
                                hypotheses.stream().map(BusinessHypothesis::id).toList(),
                                List.of(inventoryEvidence.risk().id()), Priority.HIGH, null, null,
                                List.of("Current balances are up to date."),
                                List.of("Replenishment without demand validation can create excess stock."),
                                List.of("Confirm supplier lead time", "Review current reservations", "Monitor sales velocity"), true));
                    }
                }
                case MONITOR_SLOW_MOVING_PRODUCTS -> {
                    if (inventoryEvidence.excess() != null) result.add(new Recommendation(
                            UUID.randomUUID(), "Monitor slow-moving inventory",
                            "Review products with inventory but no recent paid sales.",
                            "Current balances and selected-period sales velocity indicate potential excess inventory.",
                            findings.stream().map(BusinessFinding::id).toList(), List.of(),
                            List.of(inventoryEvidence.excess().id()), Priority.LOW, null, null,
                            List.of("The selected period is long enough to represent normal demand."),
                            List.of("Seasonal products may appear slow moving."),
                            List.of("Extend the comparison period", "Review seasonality and planned promotions"), true));
                }
            }
        }
        if (result.isEmpty()) {
            result.add(new Recommendation(
                    UUID.randomUUID(), "Continue monitoring verified drivers",
                    "Review contribution and inventory indicators before making operational changes.",
                    "The available evidence supports monitoring but not a causal or financial-impact claim.",
                    findings.stream().map(BusinessFinding::id).toList(), List.of(),
                    List.of(revenueEvidence.id()), Priority.LOW, null, null, List.of(),
                    List.of("Additional data may change the interpretation."),
                    List.of("Collect more observations", "Re-run the report after new data is available"), true));
        }
        return result;
    }

    private List<BusinessScenario> scenarios(AnalysisBundle bundle, EvidenceReference baselineEvidence) {
        return bundle.scenarios().stream().map(item -> {
            BigDecimal baseline = item.type() == BusinessAnalysisModels.ScenarioType.SALES_VELOCITY_CHANGE
                    ? item.baselineDaysOfCover() : item.baselineRevenue();
            BigDecimal modeled = item.type() == BusinessAnalysisModels.ScenarioType.SALES_VELOCITY_CHANGE
                    ? item.modeledDaysOfCover() : item.modeledRevenue();
            String unit = item.type() == BusinessAnalysisModels.ScenarioType.SALES_VELOCITY_CHANGE
                    ? "DAYS" : item.currency();
            return new BusinessScenario(
                    item.id(), item.type(), item.changePercent(),
                    "Modeled " + item.type().name().toLowerCase().replace('_', ' ') + " scenario; this is not a forecast.",
                    baseline,
                    modeled,
                    unit, item.baselineGrossProfit(), item.modeledGrossProfit(), item.grossProfitChange(),
                    List.of(baselineEvidence.id()), item.assumptions(), item.limitations());
        }).toList();
    }

    private BusinessMetric metric(
            String key,
            String label,
            BusinessAnalysisModels.MetricChange change,
            String unit,
            UUID previousEvidence,
            UUID currentEvidence) {
        return new BusinessMetric(
                key, label, change.currentValue(), change.previousValue(), change.absoluteChange(),
                change.percentageChange(), unit, List.of(previousEvidence, currentEvidence));
    }

    private EvidenceReference evidence(
            String key,
            BigDecimal value,
            String unit,
            com.stockflow.analytics.AnalyticsModels.Period period,
            Instant observedAt,
            Map<String, String> filters) {
        return new EvidenceReference(
                UUID.randomUUID(), EvidenceReference.Source.ORDER_SNAPSHOT, null, key,
                BusinessAnalysisCalculations.money(value), unit, period.startDate(), period.endDateExclusive(),
                period.timeZone(), filters, observedAt);
    }

    private String revenueStatement(AnalysisBundle bundle) {
        var change = bundle.revenue().revenueChange();
        String direction = change.absoluteChange().signum() > 0 ? "increased"
                : change.absoluteChange().signum() < 0 ? "decreased" : "did not change";
        String percentage = change.percentageChange() == null
                ? "The previous-period baseline was zero, so a percentage change is unavailable."
                : "The percentage change was " + change.percentageChange().toPlainString() + "%.";
        return "Paid-order revenue " + direction + " by " + change.absoluteChange().abs().toPlainString()
                + " " + bundle.revenue().currency() + ". " + percentage;
    }

    private Priority revenuePriority(AnalysisBundle bundle) {
        var percentage = bundle.revenue().revenueChange().percentageChange();
        if (percentage != null && percentage.compareTo(new BigDecimal("-10")) <= 0) return Priority.HIGH;
        if (bundle.revenue().revenueChange().absoluteChange().signum() != 0) return Priority.MEDIUM;
        return Priority.LOW;
    }

    private record InventoryEvidence(EvidenceReference risk, EvidenceReference excess) {
    }
}
