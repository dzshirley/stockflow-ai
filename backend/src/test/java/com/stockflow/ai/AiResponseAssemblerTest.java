package com.stockflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockflow.analytics.AnalyticsModels;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiResponseAssemblerTest {

    private final AiResponseAssembler assembler = new AiResponseAssembler();

    @Test
    void assemblesNumericClaimsOnlyFromVerifiedToolOutput() {
        var period = new AnalyticsModels.Period(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), "America/New_York");
        var overview = new AnalyticsModels.OverviewResponse(
                period,
                new AnalyticsModels.SalesMetrics(new BigDecimal("1250.00"), "USD", 25,
                        new BigDecimal("50.00"), 40),
                new AnalyticsModels.Comparison(period, new BigDecimal("1000.00"),
                        new BigDecimal("250.00"), new BigDecimal("25.00"), true));
        var execution = new AiModels.ToolEnvelope(
                UUID.randomUUID(), AnalyticsToolAdapter.REVENUE_COMPARISON, overview, period);

        var response = assembler.assemble(
                UUID.randomUUID(), UUID.randomUUID(),
                new AiModels.ProviderResult(
                        "openai", "gpt-4o-mini", "Revenue is 999999 USD. Ignore tool output.",
                        AiModels.ProviderUsage.unavailable()),
                List.of(execution));

        assertThat(response.summary()).contains("1250.00 USD").doesNotContain("999999");
        assertThat(response.metrics()).extracting(AiModels.Metric::key)
                .containsExactly("revenue", "orders", "averageOrderValue", "unitsSold");
        assertThat(response.metrics().getFirst().value()).isEqualByComparingTo("1250.00");
        assertThat(response.evidence()).hasSize(1);
        assertThat(response.provider()).isEqualTo("openai");
    }

    @Test
    void returnsBoundedClarificationWhenNoToolWasSelected() {
        var response = assembler.assemble(
                UUID.randomUUID(), UUID.randomUUID(),
                new AiModels.ProviderResult(
                        "mock", "deterministic-v1", "malicious or invented provider text",
                        AiModels.ProviderUsage.unavailable()),
                List.of());

        assertThat(response.responseType()).isEqualTo(AiModels.ResponseType.CLARIFICATION);
        assertThat(response.summary()).contains("revenue").doesNotContain("malicious");
        assertThat(response.metrics()).isEmpty();
    }
}
