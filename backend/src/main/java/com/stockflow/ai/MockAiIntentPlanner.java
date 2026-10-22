package com.stockflow.ai;

import com.stockflow.ai.AiModels.HistoryMessage;
import com.stockflow.ai.AiModels.IntentPlan;
import com.stockflow.analytics.AnalyticsModels.PeriodPreset;
import com.stockflow.analytics.AnalyticsModels.TrendInterval;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** A deterministic, offline intent router used in development, CI, and provider-free demos. */
@Component
class MockAiIntentPlanner {

    private static final Pattern NUMBER = Pattern.compile("\\b(\\d{1,3})\\b");

    IntentPlan plan(String question, List<HistoryMessage> history) {
        String normalized = question.toLowerCase(Locale.ROOT);
        if (isFollowUp(normalized)) {
            normalized = previousUserQuestion(history) + " " + normalized;
        }

        var tools = new LinkedHashSet<String>();
        boolean inventory = containsAny(normalized,
                "inventory", "stock", "reorder", "coverage", "days of cover", "run out");
        boolean risk = containsAny(normalized,
                "risk", "low stock", "out of stock", "run out", "reorder", "coverage", "days of cover");

        if (inventory) {
            tools.add(risk ? AnalyticsToolAdapter.INVENTORY_RISK : AnalyticsToolAdapter.INVENTORY_SUMMARY);
            if (containsAny(normalized, "summary", "overview") && risk) {
                tools.add(AnalyticsToolAdapter.INVENTORY_SUMMARY);
            }
        }
        boolean topProducts = (normalized.contains("top ") && normalized.contains("product"))
                || containsAny(normalized, "best product", "best seller", "selling product", "products by revenue");
        if (!inventory && topProducts) {
            tools.add(AnalyticsToolAdapter.TOP_PRODUCTS);
        }
        if (containsAny(normalized, "categor", "department")) {
            tools.add(AnalyticsToolAdapter.SALES_BY_CATEGORY);
        }
        if (containsAny(normalized, "trend", "over time", "chart", "graph", "visualize", "daily", "weekly", "monthly",
                "by day", "by week", "by month")) {
            tools.add(AnalyticsToolAdapter.REVENUE_TREND);
        }

        boolean comparison = containsAny(normalized,
                "compare", "comparison", "versus", " vs ", "growth", "change from", "previous period");
        boolean orders = Pattern.compile("\\borders?\\b").matcher(normalized).find()
                || containsAny(normalized, "average order", "aov", "basket", "units sold");
        boolean revenue = containsAny(normalized,
                "revenue", "sales", "performance", "business", "how are we doing", "overview");
        if (comparison && (revenue || orders)) {
            tools.add(AnalyticsToolAdapter.REVENUE_COMPARISON);
        } else if (orders) {
            tools.add(AnalyticsToolAdapter.ORDER_STATISTICS);
        } else if (revenue && tools.isEmpty()) {
            tools.add(AnalyticsToolAdapter.REVENUE_SUMMARY);
        }

        PeriodPreset preset = period(normalized);
        TrendInterval interval = interval(normalized);
        int limit = limit(normalized);
        return new IntentPlan(preset, null, null, interval, comparison, limit, List.copyOf(tools));
    }

    private PeriodPreset period(String question) {
        if (containsAny(question, "last month", "previous month", "prior month")) return PeriodPreset.LAST_MONTH;
        if (containsAny(question, "this month", "month to date", "mtd")) return PeriodPreset.THIS_MONTH;
        if (containsAny(question, "90 day", "90-day", "quarter")) return PeriodPreset.LAST_90_DAYS;
        if (containsAny(question, "7 day", "7-day", "last week", "past week", "this week")) return PeriodPreset.LAST_7_DAYS;
        return PeriodPreset.LAST_30_DAYS;
    }

    private TrendInterval interval(String question) {
        if (containsAny(question, "weekly", "by week", "week over week")) return TrendInterval.WEEK;
        if (containsAny(question, "monthly", "by month", "month over month")) return TrendInterval.MONTH;
        if (containsAny(question, "daily", "by day", "day over day")) return TrendInterval.DAY;
        return null;
    }

    private int limit(String question) {
        var matcher = NUMBER.matcher(question);
        while (matcher.find()) {
            int candidate = Integer.parseInt(matcher.group(1));
            if (candidate > 0 && candidate <= 100 && candidate != 7 && candidate != 30 && candidate != 90) {
                return candidate;
            }
        }
        if (question.contains("five")) return 5;
        if (question.contains("ten")) return 10;
        return 10;
    }

    private boolean isFollowUp(String question) {
        return containsAny(question, "that", "same", "it", "those", "now show", "visualize")
                && question.length() < 100;
    }

    private String previousUserQuestion(List<HistoryMessage> history) {
        for (int index = history.size() - 1; index >= 0; index--) {
            var item = history.get(index);
            if ("USER".equals(item.role())) return item.content().toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) return true;
        }
        return false;
    }
}
