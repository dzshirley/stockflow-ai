package com.stockflow.analysis;

import com.stockflow.analysis.BusinessAnalysisModels.DimensionRevenue;
import com.stockflow.analysis.BusinessAnalysisModels.MetricChange;
import com.stockflow.analysis.BusinessAnalysisModels.PriceVolumeAnalysis;
import com.stockflow.analysis.BusinessAnalysisModels.PriceVolumeItem;
import com.stockflow.analysis.BusinessAnalysisModels.ProductLifecycle;
import com.stockflow.analysis.BusinessAnalysisModels.ProductPeriodFacts;
import com.stockflow.analysis.BusinessAnalysisModels.RevenueContribution;
import com.stockflow.analysis.BusinessAnalysisModels.ScenarioResult;
import com.stockflow.analysis.BusinessAnalysisModels.ScenarioType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Pure, reproducible Phase 6 calculations. No model-authored arithmetic enters this class. */
public final class BusinessAnalysisCalculations {

    private static final int SCALE = 4;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");

    private BusinessAnalysisCalculations() {
    }

    public static MetricChange change(BigDecimal previous, BigDecimal current) {
        var normalizedPrevious = number(previous);
        var normalizedCurrent = number(current);
        var absolute = money(normalizedCurrent.subtract(normalizedPrevious));
        boolean comparable = normalizedPrevious.compareTo(BigDecimal.ZERO) != 0;
        var percentage = comparable
                ? normalizedCurrent.subtract(normalizedPrevious)
                        .divide(normalizedPrevious.abs(), SCALE, RoundingMode.HALF_UP)
                        .multiply(ONE_HUNDRED).setScale(SCALE, RoundingMode.HALF_UP)
                : null;
        return new MetricChange(money(normalizedPrevious), money(normalizedCurrent), absolute, percentage, comparable);
    }

    public static List<RevenueContribution> contributions(
            List<DimensionRevenue> dimensions, BigDecimal totalChange) {
        var total = number(totalChange);
        return dimensions.stream()
                .map(item -> {
                    var previous = money(item.previousRevenue());
                    var current = money(item.currentRevenue());
                    var contribution = money(current.subtract(previous));
                    var share = total.compareTo(BigDecimal.ZERO) == 0
                            ? null
                            : contribution.divide(total.abs(), SCALE, RoundingMode.HALF_UP)
                                    .multiply(ONE_HUNDRED).setScale(SCALE, RoundingMode.HALF_UP);
                    return new RevenueContribution(
                            item.dimensionKey(), item.label(), previous, current, contribution, share);
                })
                .sorted(Comparator.comparing(
                        (RevenueContribution value) -> value.contribution().abs()).reversed()
                        .thenComparing(RevenueContribution::label))
                .toList();
    }

    public static BigDecimal reconciliationResidual(
            BigDecimal totalChange, List<RevenueContribution> contributions) {
        var sum = contributions.stream()
                .map(RevenueContribution::contribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return money(number(totalChange).subtract(sum));
    }

    /**
     * Product-level symmetric (Shapley/midpoint) price-volume decomposition.
     * For continuing products: price=(P1-P0)(Q1+Q0)/2 and volume=(Q1-Q0)(P1+P0)/2.
     * New and discontinued products are isolated as lifecycle effects because one period has no observed price.
     */
    public static PriceVolumeAnalysis priceVolume(List<ProductPeriodFacts> facts) {
        var items = new ArrayList<PriceVolumeItem>();
        var totalChange = BigDecimal.ZERO;
        var totalPrice = BigDecimal.ZERO;
        var totalVolume = BigDecimal.ZERO;
        var totalNew = BigDecimal.ZERO;
        var totalDiscontinued = BigDecimal.ZERO;
        var totalResidual = BigDecimal.ZERO;

        for (var item : facts) {
            var previousRevenue = number(item.previousRevenue());
            var currentRevenue = number(item.currentRevenue());
            var change = money(currentRevenue.subtract(previousRevenue));
            var lifecycle = lifecycle(item.previousQuantity(), item.currentQuantity());
            BigDecimal previousPrice = item.previousQuantity() > 0
                    ? previousRevenue.divide(BigDecimal.valueOf(item.previousQuantity()), SCALE, RoundingMode.HALF_UP)
                    : null;
            BigDecimal currentPrice = item.currentQuantity() > 0
                    ? currentRevenue.divide(BigDecimal.valueOf(item.currentQuantity()), SCALE, RoundingMode.HALF_UP)
                    : null;
            var priceEffect = BigDecimal.ZERO.setScale(SCALE);
            var volumeEffect = BigDecimal.ZERO.setScale(SCALE);
            var lifecycleEffect = BigDecimal.ZERO.setScale(SCALE);

            if (lifecycle == ProductLifecycle.CONTINUING) {
                priceEffect = money(currentPrice.subtract(previousPrice)
                        .multiply(BigDecimal.valueOf(item.currentQuantity() + item.previousQuantity()))
                        .divide(TWO, SCALE, RoundingMode.HALF_UP));
                volumeEffect = money(BigDecimal.valueOf(item.currentQuantity() - item.previousQuantity())
                        .multiply(currentPrice.add(previousPrice))
                        .divide(TWO, SCALE, RoundingMode.HALF_UP));
            } else if (lifecycle == ProductLifecycle.NEW) {
                lifecycleEffect = money(currentRevenue);
                totalNew = totalNew.add(lifecycleEffect);
            } else if (lifecycle == ProductLifecycle.DISCONTINUED) {
                lifecycleEffect = money(previousRevenue.negate());
                totalDiscontinued = totalDiscontinued.add(lifecycleEffect);
            }

            var residual = money(change.subtract(priceEffect).subtract(volumeEffect).subtract(lifecycleEffect));
            totalChange = totalChange.add(change);
            totalPrice = totalPrice.add(priceEffect);
            totalVolume = totalVolume.add(volumeEffect);
            totalResidual = totalResidual.add(residual);
            items.add(new PriceVolumeItem(
                    item.productId(), item.sku(), item.name(), lifecycle,
                    item.previousQuantity(), item.currentQuantity(), previousPrice, currentPrice,
                    change, priceEffect, volumeEffect, lifecycleEffect, residual));
        }

        boolean complete = totalResidual.abs().compareTo(new BigDecimal("0.0001")) <= 0;
        return new PriceVolumeAnalysis(
                "PRODUCT_LEVEL_SYMMETRIC_SHAPLEY",
                money(totalChange), money(totalPrice), money(totalVolume), money(totalNew),
                money(totalDiscontinued), money(totalResidual), complete, items,
                List.of(
                        "Realized net unit price includes recorded line discounts.",
                        "New and discontinued products are lifecycle effects because an observed price is absent in one period.",
                        "The decomposition is descriptive and does not estimate customer response or causality."));
    }

    public static ScenarioResult scenario(
            ScenarioType type,
            BigDecimal changePercent,
            String currency,
            BigDecimal baselineRevenue,
            BigDecimal baselineGrossProfit,
            BigDecimal baselineDiscount,
            BigDecimal baselineDaysOfCover) {
        if (type == null || changePercent == null) {
            throw new IllegalArgumentException("Scenario type and change percentage are required");
        }
        if (changePercent.compareTo(new BigDecimal("-90.00")) < 0
                || changePercent.compareTo(new BigDecimal("1000.00")) > 0) {
            throw new IllegalArgumentException("Scenario change must be between -90% and 1000%");
        }
        var factor = BigDecimal.ONE.add(changePercent.divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP));
        var revenue = money(baselineRevenue);
        var profit = baselineGrossProfit == null ? null : money(baselineGrossProfit);
        var discount = baselineDiscount == null ? null : money(baselineDiscount);
        BigDecimal modeledRevenue = null;
        BigDecimal modeledProfit = null;
        BigDecimal modeledCoverage = null;
        var assumptions = new ArrayList<String>();
        var limitations = new ArrayList<String>();

        switch (type) {
            case UNIT_SALES_CHANGE -> {
                modeledRevenue = money(revenue.multiply(factor));
                modeledProfit = profit == null ? null : money(profit.multiply(factor));
                assumptions.add("Unit sales change proportionally with unchanged product mix, realized prices, discounts, and unit costs.");
            }
            case PRICE_CHANGE -> {
                modeledRevenue = money(revenue.multiply(factor));
                modeledProfit = profit == null ? null : money(profit.add(modeledRevenue.subtract(revenue)));
                assumptions.add("Sold quantities, product mix, discounts, and unit costs remain constant.");
                limitations.add("This scenario does not predict demand elasticity or competitor response.");
            }
            case DISCOUNT_CHANGE -> {
                if (discount == null) {
                    modeledRevenue = revenue;
                    limitations.add("Recorded discount totals are unavailable, so the modeled revenue is unchanged.");
                } else {
                    var additionalDiscount = money(discount.multiply(changePercent.divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP)));
                    modeledRevenue = money(revenue.subtract(additionalDiscount));
                    modeledProfit = profit == null ? null : money(profit.subtract(additionalDiscount));
                }
                assumptions.add("The percentage applies to recorded discount amount, not list price or customer demand.");
            }
            case SALES_VELOCITY_CHANGE -> {
                if (baselineDaysOfCover == null || factor.compareTo(BigDecimal.ZERO) <= 0) {
                    limitations.add("Days of cover is unavailable because baseline velocity or a valid factor is unavailable.");
                } else {
                    modeledCoverage = baselineDaysOfCover.divide(factor, SCALE, RoundingMode.HALF_UP);
                }
                assumptions.add("Available inventory is fixed and daily unit velocity changes by the requested percentage.");
                limitations.add("Coverage is a static scenario, not a demand forecast or replenishment plan.");
            }
        }

        if ((type == ScenarioType.UNIT_SALES_CHANGE || type == ScenarioType.PRICE_CHANGE
                || type == ScenarioType.DISCOUNT_CHANGE) && profit == null) {
            limitations.add("Gross profit is unavailable because reliable cost evidence was not supplied.");
        }
        return new ScenarioResult(
                UUID.randomUUID(), type, changePercent.setScale(2, RoundingMode.HALF_UP), currency,
                revenue, modeledRevenue, modeledRevenue == null ? null : money(modeledRevenue.subtract(revenue)),
                profit, modeledProfit, modeledProfit == null || profit == null ? null : money(modeledProfit.subtract(profit)),
                baselineDaysOfCover, modeledCoverage, assumptions, limitations);
    }

    public static BigDecimal money(BigDecimal value) {
        return number(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal number(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static ProductLifecycle lifecycle(long previousQuantity, long currentQuantity) {
        if (previousQuantity > 0 && currentQuantity > 0) return ProductLifecycle.CONTINUING;
        if (previousQuantity == 0 && currentQuantity > 0) return ProductLifecycle.NEW;
        if (previousQuantity > 0 && currentQuantity == 0) return ProductLifecycle.DISCONTINUED;
        return ProductLifecycle.NO_SALES;
    }
}
