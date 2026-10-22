package com.stockflow.ai;

import com.stockflow.analytics.AnalyticsModels;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AiModels {

    private AiModels() {
    }

    public enum ResponseType { ANALYTICS, CLARIFICATION, UNSUPPORTED }
    public enum ChartType { LINE, BAR }

    public record QueryRequest(
            UUID conversationId,
            @NotBlank @Size(max = 4000) String question) {
    }

    public record ConversationSummary(
            UUID id,
            String title,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ConversationList(List<ConversationSummary> items) {
    }

    public record ConversationMessage(
            UUID id,
            String role,
            String content,
            Instant createdAt,
            AiResponse response) {
    }

    public record ConversationDetail(
            UUID id,
            String title,
            Instant createdAt,
            Instant updatedAt,
            List<ConversationMessage> messages) {
    }

    public record AiResponse(
            UUID conversationId,
            UUID messageId,
            ResponseType responseType,
            String summary,
            List<Metric> metrics,
            List<Chart> charts,
            List<DataTable> tables,
            List<Evidence> evidence,
            List<String> assumptions,
            List<String> limitations,
            List<AppliedFilter> appliedFilters,
            String provider) {
    }

    public record Metric(
            String key,
            String label,
            BigDecimal value,
            String unit,
            BigDecimal comparisonValue,
            BigDecimal changePercent,
            boolean comparable) {
    }

    public record ChartSeries(String key, String label, String unit) {
    }

    public record Chart(
            String id,
            ChartType type,
            String title,
            String xKey,
            List<ChartSeries> series,
            List<Map<String, Object>> data) {
    }

    public record DataTable(
            String id,
            String title,
            List<String> columns,
            List<Map<String, Object>> rows) {
    }

    public record Evidence(
            UUID toolExecutionId,
            String toolName,
            String description,
            AnalyticsModels.Period period) {
    }

    public record AppliedFilter(
            String name,
            String value) {
    }

    record InvocationContext(
            UUID requestId,
            UUID conversationId,
            UUID messageId,
            com.stockflow.store.StoreContext storeContext) {
    }

    record ProviderRequest(
            InvocationContext invocation,
            String question,
            List<HistoryMessage> history) {
    }

    record BusinessSynthesisRequest(String evidenceBundleJson) {
    }

    record ProviderResult(
            String provider,
            String model,
            String text,
            ProviderUsage usage) {
    }

    record ProviderUsage(
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens,
            BigDecimal estimatedCostUsd,
            String pricingVersion) {
        static ProviderUsage unavailable() {
            return new ProviderUsage(null, null, null, null, null);
        }
    }

    record HistoryMessage(String role, String content) {
    }

    record ToolEnvelope(
            UUID toolExecutionId,
            String toolName,
            Object result,
            AnalyticsModels.Period period) {
    }

    record IntentPlan(
            AnalyticsModels.PeriodPreset preset,
            LocalDate startDate,
            LocalDate endDateExclusive,
            AnalyticsModels.TrendInterval interval,
            boolean comparePrevious,
            int limit,
            List<String> tools) {
    }
}
