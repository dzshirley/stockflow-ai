package com.stockflow.ai;

import com.stockflow.ai.AiConfiguration.AiProperties;
import com.stockflow.ai.AiModels.IntentPlan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "stockflow.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
class MockAiProviderGateway implements AiProviderGateway {

    private final MockAiIntentPlanner planner;
    private final AnalyticsToolAdapter tools;
    private final AiProperties properties;

    MockAiProviderGateway(MockAiIntentPlanner planner, AnalyticsToolAdapter tools, AiProperties properties) {
        this.planner = planner;
        this.tools = tools;
        this.properties = properties;
    }

    @Override
    public AiModels.ProviderResult execute(AiModels.ProviderRequest request) {
        var plan = planner.plan(request.question(), request.history());
        plan.tools().stream().limit(properties.maxToolCalls()).forEach(tool -> execute(tool, plan, request.invocation()));
        return new AiModels.ProviderResult(
                providerName(), configuredModel(),
                plan.tools().isEmpty()
                        ? "Ask about revenue, orders, products, categories, trends, or inventory."
                        : "Analytics tools completed.",
                AiModels.ProviderUsage.unavailable());
    }

    @Override
    public AiModels.ProviderResult synthesize(AiModels.BusinessSynthesisRequest request) {
        String evidence = request.evidenceBundleJson();
        boolean inventoryRisk = !evidence.contains("\"outOfStockCount\":0")
                || !evidence.contains("\"lowStockCount\":0");
        boolean excessInventory = !evidence.contains("\"excessInventoryCount\":0");
        String hypotheses = inventoryRisk ? "[\"INVENTORY_AVAILABILITY_MAY_CONTRIBUTE\"]" : "[]";
        String recommendations = "[\"REVIEW_REVENUE_DRIVERS\""
                + (inventoryRisk ? ",\"REVIEW_INVENTORY_RISK\"" : "")
                + (excessInventory ? ",\"MONITOR_SLOW_MOVING_PRODUCTS\"" : "") + "]";
        String content = "{\"hypothesisCodes\":" + hypotheses
                + ",\"recommendationCodes\":" + recommendations + "}";
        return new AiModels.ProviderResult(
                providerName(), configuredModel(), content, AiModels.ProviderUsage.unavailable());
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public String configuredModel() {
        return "deterministic-v1";
    }

    private void execute(String name, IntentPlan plan, AiModels.InvocationContext context) {
        String preset = plan.preset().name();
        String start = text(plan.startDate());
        String end = text(plan.endDateExclusive());
        switch (name) {
            case AnalyticsToolAdapter.REVENUE_SUMMARY ->
                    tools.revenueSummary(context, preset, start, end, false, name);
            case AnalyticsToolAdapter.REVENUE_COMPARISON ->
                    tools.revenueSummary(context, preset, start, end, true, name);
            case AnalyticsToolAdapter.ORDER_STATISTICS ->
                    tools.revenueSummary(context, preset, start, end, false, name);
            case AnalyticsToolAdapter.REVENUE_TREND ->
                    tools.revenueTrend(context, preset, start, end, text(plan.interval()));
            case AnalyticsToolAdapter.TOP_PRODUCTS ->
                    tools.topProducts(context, preset, start, end, Math.min(plan.limit(), 50));
            case AnalyticsToolAdapter.SALES_BY_CATEGORY ->
                    tools.salesByCategory(context, preset, start, end);
            case AnalyticsToolAdapter.INVENTORY_SUMMARY ->
                    tools.inventorySummary(context, preset, start, end);
            case AnalyticsToolAdapter.INVENTORY_RISK ->
                    tools.inventoryRisk(context, preset, start, end, plan.limit());
            default -> throw new IllegalStateException("Planner selected a non-allow-listed tool");
        }
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }
}
