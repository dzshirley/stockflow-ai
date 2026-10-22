package com.stockflow.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/** Bounded Phase 6 synthesis facade reusing the configured provider, retries, usage, and pricing. */
@Service
public class BusinessAnalysisAiService {

    private final AiProviderGateway provider;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    BusinessAnalysisAiService(AiProviderGateway provider, ObjectMapper mapper, MeterRegistry meters) {
        this.provider = provider;
        this.mapper = mapper;
        this.meters = meters;
    }

    public Synthesis synthesize(String evidenceBundleJson) {
        long started = System.nanoTime();
        var timer = Timer.start(meters);
        try {
            var result = provider.synthesize(new AiModels.BusinessSynthesisRequest(evidenceBundleJson));
            List<HypothesisCode> hypotheses;
            List<RecommendationCode> recommendations;
            try {
                var selection = mapper.readValue(result.text(), Selection.class);
                hypotheses = safeHypotheses(selection.hypothesisCodes());
                recommendations = safeRecommendations(selection.recommendationCodes());
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                meters.counter("stockflow.business_analysis.invalid_synthesis").increment();
                throw AiProviderFailureClassifier.malformed(exception);
            }
            long latencyMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
            timer.stop(meters.timer(
                    "stockflow.business_analysis.model.duration", "provider", result.provider(), "status", "success"));
            return new Synthesis(
                    hypotheses, recommendations, result.provider(), result.model(), result.usage().inputTokens(),
                    result.usage().outputTokens(), result.usage().totalTokens(),
                    result.usage().pricingVersion(), result.usage().estimatedCostUsd(), latencyMs);
        } catch (RuntimeException exception) {
            timer.stop(meters.timer(
                    "stockflow.business_analysis.model.duration", "provider", provider.providerName(), "status", "error"));
            meters.counter("stockflow.business_analysis.model.errors", "provider", provider.providerName()).increment();
            throw exception;
        }
    }

    private List<HypothesisCode> safeHypotheses(List<String> values) {
        return (values == null ? List.<String>of() : values).stream().distinct().limit(3)
                .map(value -> parse(value, HypothesisCode.class)).toList();
    }

    private List<RecommendationCode> safeRecommendations(List<String> values) {
        return (values == null ? List.<String>of() : values).stream().distinct().limit(5)
                .map(value -> parse(value, RecommendationCode.class)).toList();
    }

    private <T extends Enum<T>> T parse(String value, Class<T> type) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Provider returned an unsupported business-analysis code", exception);
        }
    }

    public enum HypothesisCode {
        INVENTORY_AVAILABILITY_MAY_CONTRIBUTE,
        PRODUCT_MIX_MAY_CONTRIBUTE
    }

    public enum RecommendationCode {
        REVIEW_REVENUE_DRIVERS,
        REVIEW_INVENTORY_RISK,
        MONITOR_SLOW_MOVING_PRODUCTS
    }

    public record Synthesis(
            List<HypothesisCode> hypothesisCodes,
            List<RecommendationCode> recommendationCodes,
            String provider,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens,
            String pricingVersion,
            BigDecimal estimatedCostUsd,
            long latencyMs) {
    }

    private record Selection(List<String> hypothesisCodes, List<String> recommendationCodes) {
    }
}
