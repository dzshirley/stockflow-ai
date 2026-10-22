package com.stockflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AiCostEstimatorTest {

    private final AiCostEstimator estimator = new AiCostEstimator(new AiConfiguration.AiPricingProperties(
            "2026-10-22", "gpt-4o-mini", new BigDecimal("0.15"), new BigDecimal("0.60")));

    @Test
    void usesProviderTokensAndVersionedPricing() {
        var usage = estimator.usage("gpt-4o-mini", 1_000, 500, 1_500);

        assertThat(usage.inputTokens()).isEqualTo(1_000);
        assertThat(usage.outputTokens()).isEqualTo(500);
        assertThat(usage.totalTokens()).isEqualTo(1_500);
        assertThat(usage.estimatedCostUsd()).isEqualByComparingTo("0.00045000");
        assertThat(usage.pricingVersion()).isEqualTo("2026-10-22");
    }

    @Test
    void retainsUsageButDoesNotGuessPriceForAnotherModel() {
        var usage = estimator.usage("unpriced-model", 10, 20, 30);

        assertThat(usage.totalTokens()).isEqualTo(30);
        assertThat(usage.estimatedCostUsd()).isNull();
        assertThat(usage.pricingVersion()).isEqualTo("2026-10-22");
    }

    @Test
    void rejectsInconsistentProviderUsage() {
        assertThatThrownBy(() -> estimator.usage("gpt-4o-mini", 10, 20, 25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inconsistent");
    }
}
