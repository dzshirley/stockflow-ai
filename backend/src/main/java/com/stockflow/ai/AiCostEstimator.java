package com.stockflow.ai;

import com.stockflow.ai.AiConfiguration.AiPricingProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
class AiCostEstimator {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);
    private final AiPricingProperties pricing;

    AiCostEstimator(AiPricingProperties pricing) {
        this.pricing = pricing;
    }

    AiModels.ProviderUsage usage(String model, Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        if (inputTokens == null || outputTokens == null || totalTokens == null) {
            return AiModels.ProviderUsage.unavailable();
        }
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0
                || totalTokens < inputTokens + outputTokens) {
            throw new IllegalArgumentException("Provider token usage is inconsistent");
        }
        if (!pricing.model().equals(model)) {
            return new AiModels.ProviderUsage(inputTokens, outputTokens, totalTokens, null, pricing.version());
        }
        var inputCost = pricing.inputUsdPerMillion()
                .multiply(BigDecimal.valueOf(inputTokens)).divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        var outputCost = pricing.outputUsdPerMillion()
                .multiply(BigDecimal.valueOf(outputTokens)).divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        return new AiModels.ProviderUsage(
                inputTokens,
                outputTokens,
                totalTokens,
                inputCost.add(outputCost).setScale(8, RoundingMode.HALF_UP),
                pricing.version());
    }
}
