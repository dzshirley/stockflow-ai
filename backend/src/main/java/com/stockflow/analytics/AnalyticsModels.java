package com.stockflow.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AnalyticsModels {

    private AnalyticsModels() {
    }

    public enum PeriodPreset {
        LAST_7_DAYS,
        LAST_30_DAYS,
        LAST_90_DAYS,
        THIS_MONTH,
        LAST_MONTH,
        CUSTOM
    }

    public enum TrendInterval {
        DAY,
        WEEK,
        MONTH
    }

    public enum InventoryRiskLevel {
        OUT_OF_STOCK,
        LOW_STOCK,
        AT_RISK,
        HEALTHY,
        NO_SALES
    }

    public record Period(
            LocalDate startDate,
            LocalDate endDateExclusive,
            String timeZone) {
    }

    public record SalesMetrics(
            BigDecimal revenue,
            String currency,
            long orders,
            BigDecimal averageOrderValue,
            long unitsSold) {
    }

    public record Comparison(
            Period period,
            BigDecimal revenue,
            BigDecimal revenueChange,
            BigDecimal revenueGrowthPercent,
            boolean comparable) {
    }

    public record OverviewResponse(
            Period period,
            SalesMetrics metrics,
            Comparison comparison) {
    }

    public record TrendPoint(
            LocalDate bucketStart,
            BigDecimal revenue,
            long orders,
            long unitsSold) {
    }

    public record RevenueTrendResponse(
            Period period,
            TrendInterval interval,
            String currency,
            List<TrendPoint> points) {
    }

    public record ProductPerformance(
            UUID productId,
            String sku,
            String name,
            BigDecimal revenue,
            long unitsSold,
            long orders) {
    }

    public record ProductPerformanceResponse(
            Period period,
            String currency,
            List<ProductPerformance> items) {
    }

    public record CategoryPerformance(
            String category,
            BigDecimal revenue,
            long unitsSold,
            long orders) {
    }

    public record CategoryPerformanceResponse(
            Period period,
            String currency,
            List<CategoryPerformance> items) {
    }

    public record InventorySummaryResponse(
            long activeProducts,
            long availableUnits,
            long reservedUnits,
            long lowStockProducts,
            long unitsSoldInPeriod,
            BigDecimal averageDailyUnitsSold,
            Period salesPeriod) {
    }

    public record InventoryRisk(
            UUID productId,
            String sku,
            String name,
            String category,
            long availableQuantity,
            long reservedQuantity,
            long reorderPoint,
            long unitsSold,
            BigDecimal averageDailyUnitsSold,
            BigDecimal daysOfCover,
            InventoryRiskLevel riskLevel) {
    }

    public record InventoryRiskResponse(
            Period salesPeriod,
            List<InventoryRisk> items) {
    }
}
