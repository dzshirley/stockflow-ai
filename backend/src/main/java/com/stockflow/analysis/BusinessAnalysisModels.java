package com.stockflow.analysis;

import com.stockflow.analytics.AnalyticsModels;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API and deterministic calculation contracts for Phase 6 business analysis. */
public final class BusinessAnalysisModels {

    private BusinessAnalysisModels() {
    }

    public enum AnalysisType {
        AUTO,
        REVENUE_INVESTIGATION,
        INVENTORY_RISK,
        SCENARIO_ANALYSIS
    }

    public enum ScenarioType {
        UNIT_SALES_CHANGE,
        PRICE_CHANGE,
        DISCOUNT_CHANGE,
        SALES_VELOCITY_CHANGE
    }

    public enum Priority {
        HIGH,
        MEDIUM,
        LOW
    }

    public record CreateReportRequest(
            @NotBlank @Size(max = 1000) String question,
            AnalysisType analysisType,
            AnalyticsModels.PeriodPreset preset,
            LocalDate startDate,
            LocalDate endDateExclusive,
            @Size(max = 5) List<@NotNull @Valid ScenarioRequest> scenarios) {

        public CreateReportRequest {
            scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
        }
    }

    public record ScenarioRequest(
            @NotNull ScenarioType type,
            @NotNull @DecimalMin("-90.00") @DecimalMax("1000.00") BigDecimal changePercent) {
    }

    public record ReportSummary(
            UUID id,
            AnalysisType reportType,
            String title,
            BusinessAnalysisReport.Status status,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            Instant dataAsOf,
            Instant createdAt,
            Instant completedAt,
            String failureCode) {
    }

    public record ReportList(List<ReportSummary> items) {
        public ReportList {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record ReportDetail(ReportSummary metadata, BusinessAnalysisReport report) {
    }

    public record SalesSnapshot(
            BigDecimal revenue,
            long orders,
            long unitsSold,
            BigDecimal averageOrderValue,
            BigDecimal grossProfit) {
    }

    public record MetricChange(
            BigDecimal previousValue,
            BigDecimal currentValue,
            BigDecimal absoluteChange,
            BigDecimal percentageChange,
            boolean comparable) {
    }

    public record RevenueContribution(
            String dimensionKey,
            String label,
            BigDecimal previousRevenue,
            BigDecimal currentRevenue,
            BigDecimal contribution,
            BigDecimal shareOfTotalChange) {
    }

    public record DimensionRevenue(
            String dimensionKey,
            String label,
            BigDecimal previousRevenue,
            BigDecimal currentRevenue) {
    }

    public record RevenueChangeAnalysis(
            AnalyticsModels.Period currentPeriod,
            AnalyticsModels.Period comparisonPeriod,
            String currency,
            SalesSnapshot previous,
            SalesSnapshot current,
            MetricChange revenueChange,
            MetricChange orderCountChange,
            MetricChange unitsSoldChange,
            MetricChange averageOrderValueChange,
            List<RevenueContribution> productContributions,
            List<RevenueContribution> categoryContributions,
            BigDecimal productResidual,
            BigDecimal categoryResidual) {

        public RevenueChangeAnalysis {
            productContributions = List.copyOf(productContributions);
            categoryContributions = List.copyOf(categoryContributions);
        }
    }

    public enum ProductLifecycle {
        CONTINUING,
        NEW,
        DISCONTINUED,
        NO_SALES
    }

    public record PriceVolumeItem(
            UUID productId,
            String sku,
            String name,
            ProductLifecycle lifecycle,
            long previousQuantity,
            long currentQuantity,
            BigDecimal previousNetUnitPrice,
            BigDecimal currentNetUnitPrice,
            BigDecimal revenueChange,
            BigDecimal priceEffect,
            BigDecimal volumeEffect,
            BigDecimal lifecycleEffect,
            BigDecimal residual) {
    }

    public record PriceVolumeAnalysis(
            String method,
            BigDecimal totalRevenueChange,
            BigDecimal priceEffect,
            BigDecimal volumeEffect,
            BigDecimal newProductEffect,
            BigDecimal discontinuedProductEffect,
            BigDecimal residual,
            boolean complete,
            List<PriceVolumeItem> items,
            List<String> limitations) {

        public PriceVolumeAnalysis {
            items = List.copyOf(items);
            limitations = List.copyOf(limitations);
        }
    }

    public record ProductPeriodFacts(
            UUID productId,
            String sku,
            String name,
            String category,
            BigDecimal previousRevenue,
            BigDecimal currentRevenue,
            long previousQuantity,
            long currentQuantity,
            BigDecimal previousCost,
            BigDecimal currentCost) {
    }

    public record InventoryDiagnostic(
            UUID productId,
            String sku,
            String name,
            String category,
            long availableQuantity,
            long reservedQuantity,
            long reorderPoint,
            long previousUnitsSold,
            long currentUnitsSold,
            BigDecimal salesChangePercent,
            BigDecimal averageDailyUnitsSold,
            BigDecimal daysOfCover,
            String riskLevel,
            boolean excessInventory,
            boolean historicalStockoutKnown) {
    }

    public record FinancialTotals(
            BigDecimal revenue,
            BigDecimal costOfGoodsSold,
            BigDecimal grossProfit,
            BigDecimal discounts,
            long unitsSold) {
    }

    public record AnalysisBundle(
            RevenueChangeAnalysis revenue,
            PriceVolumeAnalysis priceVolume,
            List<InventoryDiagnostic> inventoryDiagnostics,
            FinancialTotals currentFinancials,
            List<ScenarioResult> scenarios,
            List<String> limitations) {

        public AnalysisBundle {
            inventoryDiagnostics = List.copyOf(inventoryDiagnostics);
            scenarios = List.copyOf(scenarios);
            limitations = List.copyOf(limitations);
        }
    }

    public record ScenarioResult(
            UUID id,
            ScenarioType type,
            BigDecimal changePercent,
            String currency,
            BigDecimal baselineRevenue,
            BigDecimal modeledRevenue,
            BigDecimal revenueChange,
            BigDecimal baselineGrossProfit,
            BigDecimal modeledGrossProfit,
            BigDecimal grossProfitChange,
            BigDecimal baselineDaysOfCover,
            BigDecimal modeledDaysOfCover,
            List<String> assumptions,
            List<String> limitations) {

        public ScenarioResult {
            assumptions = List.copyOf(assumptions);
            limitations = List.copyOf(limitations);
        }
    }
}
