package com.stockflow.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.ai.AiModels.InvocationContext;
import com.stockflow.ai.AiModels.ToolEnvelope;
import com.stockflow.analytics.AnalyticsModels;
import com.stockflow.analytics.AnalyticsService;
import com.stockflow.common.error.ApiException;
import com.stockflow.common.error.ApiExceptions;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The complete, allow-listed boundary between an AI provider and StockFlow data.
 * Store and user identity are supplied by trusted server context and are never model arguments.
 */
@Component
class AnalyticsToolAdapter {

    static final String CONTEXT_KEY = "stockflowInvocation";
    static final String REVENUE_SUMMARY = "get_revenue_summary";
    static final String REVENUE_COMPARISON = "compare_revenue";
    static final String REVENUE_TREND = "get_revenue_trend";
    static final String TOP_PRODUCTS = "get_top_selling_products";
    static final String SALES_BY_CATEGORY = "get_sales_by_category";
    static final String ORDER_STATISTICS = "get_order_statistics";
    static final String INVENTORY_SUMMARY = "get_inventory_summary";
    static final String INVENTORY_RISK = "get_inventory_risk";

    private final AnalyticsService analytics;
    private final AiExecutionCollector collector;
    private final AiToolExecutionLogger executionLogger;
    private final ObjectMapper objectMapper;

    AnalyticsToolAdapter(
            AnalyticsService analytics,
            AiExecutionCollector collector,
            AiToolExecutionLogger executionLogger,
            ObjectMapper objectMapper) {
        this.analytics = analytics;
        this.collector = collector;
        this.executionLogger = executionLogger;
        this.objectMapper = objectMapper;
    }

    @Tool(name = REVENUE_SUMMARY, description = "Get verified revenue, order count, average order value, and units sold for a period.")
    public ToolEnvelope getRevenueSummary(
            @ToolParam(description = "Period preset: LAST_7_DAYS, LAST_30_DAYS, LAST_90_DAYS, THIS_MONTH, LAST_MONTH, or CUSTOM") String preset,
            @ToolParam(description = "Inclusive start date for CUSTOM, formatted YYYY-MM-DD", required = false) String startDate,
            @ToolParam(description = "Exclusive end date for CUSTOM, formatted YYYY-MM-DD", required = false) String endDateExclusive,
            ToolContext toolContext) {
        return revenueSummary(context(toolContext), preset, startDate, endDateExclusive, false, REVENUE_SUMMARY);
    }

    @Tool(name = REVENUE_COMPARISON, description = "Compare verified revenue for a period with the immediately preceding equal-length period.")
    public ToolEnvelope compareRevenue(
            @ToolParam(description = "Period preset: LAST_7_DAYS, LAST_30_DAYS, LAST_90_DAYS, THIS_MONTH, LAST_MONTH, or CUSTOM") String preset,
            @ToolParam(description = "Inclusive start date for CUSTOM, formatted YYYY-MM-DD", required = false) String startDate,
            @ToolParam(description = "Exclusive end date for CUSTOM, formatted YYYY-MM-DD", required = false) String endDateExclusive,
            ToolContext toolContext) {
        return revenueSummary(context(toolContext), preset, startDate, endDateExclusive, true, REVENUE_COMPARISON);
    }

    @Tool(name = REVENUE_TREND, description = "Get a verified revenue, orders, and units time series for a period.")
    public ToolEnvelope getRevenueTrend(
            @ToolParam(description = "Period preset") String preset,
            @ToolParam(description = "Inclusive start date for CUSTOM, formatted YYYY-MM-DD", required = false) String startDate,
            @ToolParam(description = "Exclusive end date for CUSTOM, formatted YYYY-MM-DD", required = false) String endDateExclusive,
            @ToolParam(description = "Optional bucket interval: DAY, WEEK, or MONTH", required = false) String interval,
            ToolContext toolContext) {
        return revenueTrend(context(toolContext), preset, startDate, endDateExclusive, interval);
    }

    @Tool(name = TOP_PRODUCTS, description = "Get verified top-selling products ranked by revenue for a period.")
    public ToolEnvelope getTopSellingProducts(
            @ToolParam(description = "Period preset") String preset,
            @ToolParam(description = "Inclusive start date for CUSTOM, formatted YYYY-MM-DD", required = false) String startDate,
            @ToolParam(description = "Exclusive end date for CUSTOM, formatted YYYY-MM-DD", required = false) String endDateExclusive,
            @ToolParam(description = "Number of products, from 1 through 50") int limit,
            ToolContext toolContext) {
        return topProducts(context(toolContext), preset, startDate, endDateExclusive, limit);
    }

    @Tool(name = SALES_BY_CATEGORY, description = "Get verified revenue, order, and unit totals grouped by product category for a period.")
    public ToolEnvelope getSalesByCategory(
            @ToolParam(description = "Period preset") String preset,
            @ToolParam(description = "Inclusive start date for CUSTOM, formatted YYYY-MM-DD", required = false) String startDate,
            @ToolParam(description = "Exclusive end date for CUSTOM, formatted YYYY-MM-DD", required = false) String endDateExclusive,
            ToolContext toolContext) {
        return salesByCategory(context(toolContext), preset, startDate, endDateExclusive);
    }

    @Tool(name = ORDER_STATISTICS, description = "Get verified order count, average order value, units sold, and revenue for a period.")
    public ToolEnvelope getOrderStatistics(
            @ToolParam(description = "Period preset") String preset,
            @ToolParam(description = "Inclusive start date for CUSTOM, formatted YYYY-MM-DD", required = false) String startDate,
            @ToolParam(description = "Exclusive end date for CUSTOM, formatted YYYY-MM-DD", required = false) String endDateExclusive,
            ToolContext toolContext) {
        return revenueSummary(context(toolContext), preset, startDate, endDateExclusive, false, ORDER_STATISTICS);
    }

    @Tool(name = INVENTORY_SUMMARY, description = "Get verified stock totals, reserved units, low-stock count, and sales velocity.")
    public ToolEnvelope getInventorySummary(
            @ToolParam(description = "Sales-velocity period preset") String preset,
            @ToolParam(description = "Inclusive start date for CUSTOM, formatted YYYY-MM-DD", required = false) String startDate,
            @ToolParam(description = "Exclusive end date for CUSTOM, formatted YYYY-MM-DD", required = false) String endDateExclusive,
            ToolContext toolContext) {
        return inventorySummary(context(toolContext), preset, startDate, endDateExclusive);
    }

    @Tool(name = INVENTORY_RISK, description = "Get verified products at inventory risk with stock, reorder point, velocity, and days of cover.")
    public ToolEnvelope getInventoryRisk(
            @ToolParam(description = "Sales-velocity period preset") String preset,
            @ToolParam(description = "Inclusive start date for CUSTOM, formatted YYYY-MM-DD", required = false) String startDate,
            @ToolParam(description = "Exclusive end date for CUSTOM, formatted YYYY-MM-DD", required = false) String endDateExclusive,
            @ToolParam(description = "Maximum products, from 1 through 100") int limit,
            ToolContext toolContext) {
        return inventoryRisk(context(toolContext), preset, startDate, endDateExclusive, limit);
    }

    ToolEnvelope revenueSummary(InvocationContext context, String preset, String start, String end, boolean compare, String name) {
        var parsed = period(preset, start, end);
        return execute(context, name, metadata(parsed, Map.of("comparePrevious", compare)), () ->
                analytics.getRevenueSummary(context.storeContext(), parsed.preset(), parsed.start(), parsed.end(), compare));
    }

    ToolEnvelope revenueTrend(InvocationContext context, String preset, String start, String end, String interval) {
        var parsed = period(preset, start, end);
        var parsedInterval = optionalEnum(interval, AnalyticsModels.TrendInterval.class, "AI_INVALID_INTERVAL");
        return execute(context, REVENUE_TREND, metadata(parsed, Map.of("interval", value(parsedInterval))), () ->
                analytics.getRevenueTrend(context.storeContext(), parsed.preset(), parsed.start(), parsed.end(), parsedInterval));
    }

    ToolEnvelope topProducts(InvocationContext context, String preset, String start, String end, int limit) {
        requireRange(limit, 1, 50, "AI_INVALID_PRODUCT_LIMIT");
        var parsed = period(preset, start, end);
        return execute(context, TOP_PRODUCTS, metadata(parsed, Map.of("limit", limit)), () ->
                analytics.getTopProducts(context.storeContext(), parsed.preset(), parsed.start(), parsed.end(), limit));
    }

    ToolEnvelope salesByCategory(InvocationContext context, String preset, String start, String end) {
        var parsed = period(preset, start, end);
        return execute(context, SALES_BY_CATEGORY, metadata(parsed, Map.of()), () ->
                analytics.getSalesByCategory(context.storeContext(), parsed.preset(), parsed.start(), parsed.end()));
    }

    ToolEnvelope inventorySummary(InvocationContext context, String preset, String start, String end) {
        var parsed = period(preset, start, end);
        return execute(context, INVENTORY_SUMMARY, metadata(parsed, Map.of()), () ->
                analytics.getInventorySummary(context.storeContext(), parsed.preset(), parsed.start(), parsed.end()));
    }

    ToolEnvelope inventoryRisk(InvocationContext context, String preset, String start, String end, int limit) {
        requireRange(limit, 1, 100, "AI_INVALID_INVENTORY_LIMIT");
        var parsed = period(preset, start, end);
        return execute(context, INVENTORY_RISK, metadata(parsed, Map.of("limit", limit)), () ->
                analytics.getInventoryRisk(context.storeContext(), parsed.preset(), parsed.start(), parsed.end(), limit));
    }

    private ToolEnvelope execute(InvocationContext context, String name, String metadata, Supplier<?> query) {
        UUID executionId = UUID.randomUUID();
        long started = System.nanoTime();
        try {
            Object result = query.get();
            var envelope = new ToolEnvelope(executionId, name, result, extractPeriod(result));
            collector.add(context.requestId(), envelope);
            executionLogger.record(executionId, context, name, AiToolExecution.Status.SUCCEEDED,
                    elapsedMillis(started), metadata, null);
            return envelope;
        } catch (RuntimeException exception) {
            executionLogger.record(executionId, context, name, AiToolExecution.Status.FAILED,
                    elapsedMillis(started), metadata,
                    exception instanceof ApiException apiException ? apiException.code() : "AI_TOOL_FAILURE");
            throw exception;
        }
    }

    private InvocationContext context(ToolContext toolContext) {
        Object value = toolContext.getContext().get(CONTEXT_KEY);
        if (value instanceof InvocationContext invocation) return invocation;
        throw new IllegalStateException("Trusted AI invocation context is missing");
    }

    private PeriodInput period(String rawPreset, String rawStart, String rawEnd) {
        var preset = requiredEnum(rawPreset, AnalyticsModels.PeriodPreset.class, "AI_INVALID_PERIOD");
        LocalDate start = optionalDate(rawStart);
        LocalDate end = optionalDate(rawEnd);
        if (preset == AnalyticsModels.PeriodPreset.CUSTOM && (start == null || end == null)) {
            throw ApiExceptions.badRequest("AI_CUSTOM_PERIOD_REQUIRED", "CUSTOM periods require start and exclusive end dates");
        }
        return new PeriodInput(preset, start, end);
    }

    private AnalyticsModels.Period extractPeriod(Object result) {
        return switch (result) {
            case AnalyticsModels.OverviewResponse value -> value.period();
            case AnalyticsModels.RevenueTrendResponse value -> value.period();
            case AnalyticsModels.ProductPerformanceResponse value -> value.period();
            case AnalyticsModels.CategoryPerformanceResponse value -> value.period();
            case AnalyticsModels.InventorySummaryResponse value -> value.salesPeriod();
            case AnalyticsModels.InventoryRiskResponse value -> value.salesPeriod();
            default -> throw new IllegalStateException("Unsupported analytics tool result");
        };
    }

    private String metadata(PeriodInput period, Map<String, Object> extra) {
        var values = new LinkedHashMap<String, Object>();
        values.put("preset", period.preset().name());
        if (period.start() != null) values.put("startDate", period.start());
        if (period.end() != null) values.put("endDateExclusive", period.end());
        values.putAll(extra);
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize safe tool metadata", exception);
        }
    }

    private LocalDate optionalDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException exception) {
            throw ApiExceptions.badRequest("AI_INVALID_DATE", "Tool dates must use YYYY-MM-DD");
        }
    }

    private <T extends Enum<T>> T requiredEnum(String value, Class<T> type, String code) {
        if (value == null || value.isBlank()) throw ApiExceptions.badRequest(code, "A required tool parameter is missing");
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ApiExceptions.badRequest(code, "The tool parameter is not supported");
        }
    }

    private <T extends Enum<T>> T optionalEnum(String value, Class<T> type, String code) {
        return value == null || value.isBlank() ? null : requiredEnum(value, type, code);
    }

    private void requireRange(int value, int minimum, int maximum, String code) {
        if (value < minimum || value > maximum) {
            throw ApiExceptions.badRequest(code, "Tool limit must be between " + minimum + " and " + maximum);
        }
    }

    private long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private String value(Object value) {
        return value == null ? "AUTO" : value.toString();
    }

    private record PeriodInput(AnalyticsModels.PeriodPreset preset, LocalDate start, LocalDate end) {
    }
}
