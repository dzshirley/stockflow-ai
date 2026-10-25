package com.stockflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.analytics.AnalyticsModels;
import com.stockflow.analytics.AnalyticsService;
import com.stockflow.auth.AuthModels;
import com.stockflow.auth.AuthenticationService;
import com.stockflow.order.OrderModels;
import com.stockflow.order.OrderService;
import com.stockflow.product.ProductModels;
import com.stockflow.product.ProductService;
import com.stockflow.store.StoreContext;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Billable evaluation. It is skipped unless RUN_LIVE_AI_EVAL=true and never records credentials or prompts.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:live_ai_eval;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "stockflow.ai.provider=openai",
        "spring.ai.model.chat=openai",
        "spring.ai.openai.api-key=${OPENAI_API_KEY}",
        "spring.ai.openai.chat.model=${AI_MODEL:gpt-4o-mini}",
        "spring.ai.openai.chat.max-tokens=300",
        "stockflow.ai.resilience.max-attempts=1",
        "stockflow.ai.max-tool-calls=4",
        "spring.ai.tools.limits.max-total-tool-calls=4"
})
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI_EVAL", matches = "(?i)true")
@Timeout(120)
class LiveOpenAiEvaluationIT {

    private static final int MAX_EVALUATION_TASKS = 3;
    private static final int MAX_TOOL_CALLS_PER_TASK = 4;
    private static final int MAX_PROVIDER_TURNS = MAX_EVALUATION_TASKS * (MAX_TOOL_CALLS_PER_TASK + 1);
    private static final BigDecimal HARD_MAX_ESTIMATED_COST_USD = new BigDecimal("0.05");

    @Autowired AuthenticationService authenticationService;
    @Autowired ProductService productService;
    @Autowired OrderService orderService;
    @Autowired AnalyticsService analyticsService;
    @Autowired AiAnalyticsService aiAnalyticsService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @Test
    void selectsToolsMatchesDeterministicMetricsAndHandlesFollowUp() throws Exception {
        var observations = new ArrayList<Map<String, Object>>();
        String failure = null;
        try {
            var costLimit = configuredCostLimit();
            var context = seedStore();
            var expected = analyticsService.getRevenueSummary(
                    context, AnalyticsModels.PeriodPreset.LAST_30_DAYS, null, null, false);

            var revenue = timedQuery(
                    context, new AiModels.QueryRequest(null, "Show revenue for the last 30 days"), observations);
            var multiStep = timedQuery(
                    context,
                    new AiModels.QueryRequest(null,
                            "Compare revenue with the previous 30 days and show the top products"),
                    observations);
            var followUp = timedQuery(
                    context,
                    new AiModels.QueryRequest(revenue.conversationId(), "Visualize that as a daily trend"),
                    observations);

            assertThat(revenue.metrics().stream()
                    .filter(metric -> metric.key().equals("revenue"))
                    .findFirst().orElseThrow().value())
                    .isEqualByComparingTo(expected.metrics().revenue());
            assertThat(revenue.evidence()).extracting(AiModels.Evidence::toolName)
                    .contains(AnalyticsToolAdapter.REVENUE_COMPARISON);
            assertThat(multiStep.evidence()).extracting(AiModels.Evidence::toolName)
                    .contains(AnalyticsToolAdapter.REVENUE_COMPARISON, AnalyticsToolAdapter.TOP_PRODUCTS);
            assertThat(followUp.charts()).extracting(AiModels.Chart::id).contains("revenue-trend");
            var revenueTools = toolMetadata(revenue);
            var multiStepTools = toolMetadata(multiStep);
            var followUpTools = toolMetadata(followUp);
            assertThat(revenueTools.get(AnalyticsToolAdapter.REVENUE_COMPARISON))
                    .containsEntry("preset", "LAST_30_DAYS")
                    .containsEntry("comparePrevious", true);
            assertThat(multiStepTools.get(AnalyticsToolAdapter.REVENUE_COMPARISON))
                    .containsEntry("preset", "LAST_30_DAYS")
                    .containsEntry("comparePrevious", true);
            assertThat(multiStepTools.get(AnalyticsToolAdapter.TOP_PRODUCTS))
                    .containsEntry("preset", "LAST_30_DAYS");
            assertThat(((Number) multiStepTools.get(AnalyticsToolAdapter.TOP_PRODUCTS).get("limit")).intValue())
                    .isBetween(1, 50);
            assertThat(followUpTools.get(AnalyticsToolAdapter.REVENUE_TREND))
                    .containsEntry("preset", "LAST_30_DAYS")
                    .containsEntry("interval", "DAY");
            assertThat(observations).hasSize(MAX_EVALUATION_TASKS);
            observations.add(Map.of("toolAudits", Map.of(
                    "revenue", revenueTools,
                    "multiStep", multiStepTools,
                    "followUp", followUpTools)));

            var aggregate = jdbcTemplate.queryForMap("""
                    SELECT MAX(model) AS model,
                           COALESCE(SUM(total_tokens), 0) AS total_tokens,
                           COALESCE(SUM(estimated_cost_usd), 0) AS estimated_cost_usd,
                           COALESCE(SUM(tool_call_count), 0) AS tool_calls
                    FROM ai_request_telemetry
                    WHERE store_id = ? AND status = 'SUCCEEDED'
                    """, context.storeId());
            var cost = new BigDecimal(aggregate.get("estimated_cost_usd").toString());
            assertThat(cost).isLessThanOrEqualTo(costLimit);
            observations.add(Map.of(
                    "model", String.valueOf(aggregate.get("model")),
                    "totalTokens", aggregate.get("total_tokens"),
                    "toolCalls", aggregate.get("tool_calls"),
                    "estimatedCostUsd", cost,
                    "costLimitUsd", costLimit));
        } catch (RuntimeException | AssertionError exception) {
            failure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            throw exception;
        } finally {
            writeResult(observations, failure);
        }
    }

    private BigDecimal configuredCostLimit() {
        var configured = new BigDecimal(System.getenv().getOrDefault(
                "AI_LIVE_MAX_COST_USD", HARD_MAX_ESTIMATED_COST_USD.toPlainString()));
        assertThat(configured).isPositive().isLessThanOrEqualTo(HARD_MAX_ESTIMATED_COST_USD);
        return configured;
    }

    private Map<String, Map<String, Object>> toolMetadata(AiModels.AiResponse response) throws Exception {
        var result = new LinkedHashMap<String, Map<String, Object>>();
        for (var evidence : response.evidence()) {
            String metadata = jdbcTemplate.queryForObject(
                    "SELECT input_metadata FROM ai_tool_executions WHERE id = ?",
                    String.class, evidence.toolExecutionId());
            @SuppressWarnings("unchecked")
            var parsed = (Map<String, Object>) objectMapper.readValue(metadata, Map.class);
            result.put(evidence.toolName(), parsed);
        }
        return result;
    }

    private StoreContext seedStore() {
        var principal = authenticationService.register(new AuthModels.RegisterRequest(
                "live-eval@example.invalid", "a-secure-password", "Live Eval", "Live Eval Store",
                "USD", "America/New_York"));
        var context = new StoreContext(principal.userId(), principal.storeId(), principal.role());
        var product = productService.create(context, new ProductModels.CreateRequest(
                "LIVE-EVAL", "Live Evaluation Product", "Evaluation", new BigDecimal("10.0000"),
                new BigDecimal("4.0000"), 10L, 1L));
        var order = orderService.create(context, "live-eval-order", new OrderModels.CreateRequest(
                List.of(new OrderModels.LineRequest(product.id(), 2))));
        orderService.payment(context, order.id(), OrderModels.PaymentOutcome.APPROVED);
        return context;
    }

    private AiModels.AiResponse timedQuery(
            StoreContext context,
            AiModels.QueryRequest request,
            List<Map<String, Object>> observations) {
        Instant started = Instant.now();
        try {
            var response = aiAnalyticsService.query(context, request);
            observations.add(Map.of(
                    "success", true,
                    "latencyMs", Duration.between(started, Instant.now()).toMillis(),
                    "responseType", response.responseType().name(),
                    "toolCalls", response.evidence().size()));
            return response;
        } catch (RuntimeException exception) {
            observations.add(Map.of(
                    "success", false,
                    "latencyMs", Duration.between(started, Instant.now()).toMillis(),
                    "failureType", exception.getClass().getSimpleName()));
            throw exception;
        }
    }

    private void writeResult(List<Map<String, Object>> observations, String failure) throws Exception {
        var result = new LinkedHashMap<String, Object>();
        result.put("recordedAt", Instant.now().toString());
        result.put("provider", "openai");
        result.put("configuredModel", System.getenv().getOrDefault("AI_MODEL", "gpt-4o-mini"));
        result.put("evaluationTaskLimit", MAX_EVALUATION_TASKS);
        result.put("maximumProviderTurnLimit", MAX_PROVIDER_TURNS);
        result.put("success", failure == null);
        result.put("failure", failure);
        result.put("observations", observations);
        Path target = Path.of("target", "live-ai-evaluation.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
}
