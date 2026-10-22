package com.stockflow.ai;

import com.stockflow.ai.AiModels.AiResponse;
import com.stockflow.ai.AiModels.AppliedFilter;
import com.stockflow.ai.AiModels.Chart;
import com.stockflow.ai.AiModels.ChartSeries;
import com.stockflow.ai.AiModels.ChartType;
import com.stockflow.ai.AiModels.DataTable;
import com.stockflow.ai.AiModels.Evidence;
import com.stockflow.ai.AiModels.Metric;
import com.stockflow.ai.AiModels.ResponseType;
import com.stockflow.ai.AiModels.ToolEnvelope;
import com.stockflow.analytics.AnalyticsModels;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds the public response only from typed, server-verified tool results. */
@Component
class AiResponseAssembler {

    AiResponse assemble(
            UUID conversationId,
            UUID messageId,
            AiModels.ProviderResult providerResult,
            List<ToolEnvelope> executions) {
        if (executions.isEmpty()) {
            return new AiResponse(
                    conversationId,
                    messageId,
                    ResponseType.CLARIFICATION,
                    "I can analyze revenue, orders, product or category performance, sales trends, and inventory risk. Try asking for one of those with a time period.",
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of("Only read-only business analytics questions are supported."),
                    List.of(), providerResult.provider());
        }

        var metrics = new ArrayList<Metric>();
        var charts = new ArrayList<Chart>();
        var tables = new ArrayList<DataTable>();
        var evidence = new ArrayList<Evidence>();
        var limitations = new LinkedHashSet<String>();
        String summary = null;

        for (ToolEnvelope envelope : executions) {
            evidence.add(new Evidence(
                    envelope.toolExecutionId(), envelope.toolName(), description(envelope.toolName()), envelope.period()));
            switch (envelope.result()) {
                case AnalyticsModels.OverviewResponse result -> {
                    addOverview(metrics, result);
                    if (summary == null) summary = overviewSummary(result);
                    limitations.add("Revenue and order metrics include completed orders in the selected period.");
                }
                case AnalyticsModels.RevenueTrendResponse result -> {
                    charts.add(trendChart(result));
                    if (summary == null) summary = "The verified revenue trend contains " + result.points().size()
                            + " " + result.interval().name().toLowerCase() + " data points.";
                    limitations.add("Trend buckets use the store time zone shown in the applied filters.");
                }
                case AnalyticsModels.ProductPerformanceResponse result -> {
                    charts.add(productsChart(result));
                    tables.add(productsTable(result));
                    if (summary == null) summary = result.items().isEmpty()
                            ? "No completed product sales were found in the selected period."
                            : result.items().getFirst().name() + " was the highest-revenue product in the selected period.";
                }
                case AnalyticsModels.CategoryPerformanceResponse result -> {
                    charts.add(categoriesChart(result));
                    tables.add(categoriesTable(result));
                    if (summary == null) summary = result.items().isEmpty()
                            ? "No completed category sales were found in the selected period."
                            : result.items().getFirst().category() + " was the highest-revenue category in the selected period.";
                }
                case AnalyticsModels.InventorySummaryResponse result -> {
                    addInventorySummary(metrics, result);
                    if (summary == null) summary = "There are " + result.availableUnits() + " available units across "
                            + result.activeProducts() + " active products; " + result.lowStockProducts() + " are low on stock.";
                    limitations.add("Inventory velocity is estimated from completed sales in the selected lookback period.");
                }
                case AnalyticsModels.InventoryRiskResponse result -> {
                    tables.add(inventoryRiskTable(result));
                    if (summary == null) summary = result.items().isEmpty()
                            ? "No inventory-risk products were found for the selected lookback period."
                            : result.items().size() + " products were returned by the inventory-risk ranking.";
                    limitations.add("Days of cover is an estimate based on recent sales velocity, not a forecast or purchase recommendation.");
                }
                default -> throw new IllegalStateException("Unrecognized verified analytics result");
            }
        }

        return new AiResponse(
                conversationId,
                messageId,
                ResponseType.ANALYTICS,
                summary == null ? "Verified analytics are ready." : summary,
                List.copyOf(metrics),
                List.copyOf(charts),
                List.copyOf(tables),
                List.copyOf(evidence),
                List.of("Currency values use the store's configured currency."),
                List.copyOf(limitations),
                filters(executions),
                providerResult.provider());
    }

    private void addOverview(List<Metric> target, AnalyticsModels.OverviewResponse value) {
        var sales = value.metrics();
        var comparison = value.comparison();
        target.add(new Metric("revenue", "Revenue", sales.revenue(), sales.currency(),
                comparison == null ? null : comparison.revenue(),
                comparison == null ? null : comparison.revenueGrowthPercent(),
                comparison != null && comparison.comparable()));
        target.add(new Metric("orders", "Orders", decimal(sales.orders()), "orders", null, null, false));
        target.add(new Metric("averageOrderValue", "Average order value", sales.averageOrderValue(), sales.currency(), null, null, false));
        target.add(new Metric("unitsSold", "Units sold", decimal(sales.unitsSold()), "units", null, null, false));
    }

    private void addInventorySummary(List<Metric> target, AnalyticsModels.InventorySummaryResponse value) {
        target.add(new Metric("activeProducts", "Active products", decimal(value.activeProducts()), "products", null, null, false));
        target.add(new Metric("availableUnits", "Available units", decimal(value.availableUnits()), "units", null, null, false));
        target.add(new Metric("reservedUnits", "Reserved units", decimal(value.reservedUnits()), "units", null, null, false));
        target.add(new Metric("lowStockProducts", "Low-stock products", decimal(value.lowStockProducts()), "products", null, null, false));
        target.add(new Metric("dailySalesVelocity", "Daily sales velocity", value.averageDailyUnitsSold(), "units/day", null, null, false));
    }

    private Chart trendChart(AnalyticsModels.RevenueTrendResponse result) {
        var rows = result.points().stream().map(point -> row(
                "date", point.bucketStart().toString(),
                "revenue", point.revenue(),
                "orders", point.orders(),
                "units", point.unitsSold())).toList();
        return new Chart("revenue-trend", ChartType.LINE, "Revenue trend", "date",
                List.of(
                        new ChartSeries("revenue", "Revenue", result.currency()),
                        new ChartSeries("orders", "Orders", "orders")), rows);
    }

    private Chart productsChart(AnalyticsModels.ProductPerformanceResponse result) {
        var rows = result.items().stream().map(item -> row(
                "name", item.name(), "revenue", item.revenue(), "units", item.unitsSold())).toList();
        return new Chart("top-products", ChartType.BAR, "Top products", "name",
                List.of(new ChartSeries("revenue", "Revenue", result.currency())), rows);
    }

    private DataTable productsTable(AnalyticsModels.ProductPerformanceResponse result) {
        var rows = result.items().stream().map(item -> row(
                "sku", item.sku(), "product", item.name(), "revenue", item.revenue(),
                "unitsSold", item.unitsSold(), "orders", item.orders())).toList();
        return new DataTable("top-products-table", "Product performance",
                List.of("sku", "product", "revenue", "unitsSold", "orders"), rows);
    }

    private Chart categoriesChart(AnalyticsModels.CategoryPerformanceResponse result) {
        var rows = result.items().stream().map(item -> row(
                "category", item.category(), "revenue", item.revenue(), "units", item.unitsSold())).toList();
        return new Chart("sales-by-category", ChartType.BAR, "Sales by category", "category",
                List.of(new ChartSeries("revenue", "Revenue", result.currency())), rows);
    }

    private DataTable categoriesTable(AnalyticsModels.CategoryPerformanceResponse result) {
        var rows = result.items().stream().map(item -> row(
                "category", item.category(), "revenue", item.revenue(),
                "unitsSold", item.unitsSold(), "orders", item.orders())).toList();
        return new DataTable("categories-table", "Category performance",
                List.of("category", "revenue", "unitsSold", "orders"), rows);
    }

    private DataTable inventoryRiskTable(AnalyticsModels.InventoryRiskResponse result) {
        var rows = result.items().stream().map(item -> row(
                "sku", item.sku(), "product", item.name(), "category", item.category(),
                "available", item.availableQuantity(), "reserved", item.reservedQuantity(),
                "reorderPoint", item.reorderPoint(), "dailyVelocity", item.averageDailyUnitsSold(),
                "daysOfCover", item.daysOfCover(), "risk", item.riskLevel().name())).toList();
        return new DataTable("inventory-risk-table", "Inventory risk",
                List.of("sku", "product", "category", "available", "reserved", "reorderPoint",
                        "dailyVelocity", "daysOfCover", "risk"), rows);
    }

    private List<AppliedFilter> filters(List<ToolEnvelope> executions) {
        var periods = new LinkedHashSet<AnalyticsModels.Period>();
        executions.forEach(item -> periods.add(item.period()));
        var result = new ArrayList<AppliedFilter>();
        int index = 1;
        for (var period : periods) {
            String prefix = periods.size() == 1 ? "" : "period" + index + ".";
            result.add(new AppliedFilter(prefix + "startDate", period.startDate().toString()));
            result.add(new AppliedFilter(prefix + "endDateExclusive", period.endDateExclusive().toString()));
            result.add(new AppliedFilter(prefix + "timeZone", period.timeZone()));
            index++;
        }
        return List.copyOf(result);
    }

    private String overviewSummary(AnalyticsModels.OverviewResponse value) {
        LocalDate inclusiveEnd = value.period().endDateExclusive().minusDays(1);
        return "Verified revenue was " + value.metrics().revenue().toPlainString() + " "
                + value.metrics().currency() + " across " + value.metrics().orders()
                + " completed orders from " + value.period().startDate() + " through " + inclusiveEnd + ".";
    }

    private String description(String toolName) {
        return switch (toolName) {
            case AnalyticsToolAdapter.REVENUE_SUMMARY -> "Verified revenue overview query";
            case AnalyticsToolAdapter.REVENUE_COMPARISON -> "Verified current and previous-period revenue query";
            case AnalyticsToolAdapter.REVENUE_TREND -> "Verified revenue time-series query";
            case AnalyticsToolAdapter.TOP_PRODUCTS -> "Verified product performance query";
            case AnalyticsToolAdapter.SALES_BY_CATEGORY -> "Verified category performance query";
            case AnalyticsToolAdapter.ORDER_STATISTICS -> "Verified order statistics query";
            case AnalyticsToolAdapter.INVENTORY_SUMMARY -> "Verified inventory summary query";
            case AnalyticsToolAdapter.INVENTORY_RISK -> "Verified inventory risk query";
            default -> "Verified analytics query";
        };
    }

    private BigDecimal decimal(long value) {
        return BigDecimal.valueOf(value);
    }

    private Map<String, Object> row(Object... values) {
        var row = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }
}
