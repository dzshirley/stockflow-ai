package com.stockflow.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The model may select from server-provided candidates; it cannot author quantities or mutations. */
@Service
public class RestockProposalAiService {
    private final AiProviderGateway provider;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    RestockProposalAiService(AiProviderGateway provider, ObjectMapper mapper, MeterRegistry meters) {
        this.provider = provider; this.mapper = mapper; this.meters = meters;
    }

    public Selection select(String question, List<Candidate> candidates) {
        long started = System.nanoTime();
        var timer = Timer.start(meters);
        try {
            String input = mapper.writeValueAsString(new Input(question, candidates));
            var result = provider.selectRestock(new AiModels.RestockSelectionRequest(input));
            var parsed = mapper.readValue(result.text(), ProviderSelection.class);
            Set<UUID> allowed = candidates.stream().map(Candidate::productId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            var selected = (parsed.selectedProductIds() == null ? List.<UUID>of() : parsed.selectedProductIds())
                    .stream().distinct().limit(candidates.size()).toList();
            if (selected.isEmpty() || !allowed.containsAll(selected)) {
                throw new IllegalArgumentException("Provider returned missing or unauthorized candidate IDs");
            }
            timer.stop(meters.timer("stockflow.restock.ai.duration", "provider", result.provider(), "status", "success"));
            return new Selection(selected, result.provider(), result.model(), result.usage().inputTokens(),
                    result.usage().outputTokens(), result.usage().totalTokens(),
                    result.usage().estimatedCostUsd(), result.usage().pricingVersion(),
                    Math.max(0, (System.nanoTime() - started) / 1_000_000));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            timer.stop(meters.timer("stockflow.restock.ai.duration", "provider", provider.providerName(), "status", "error"));
            meters.counter("stockflow.restock.ai.errors", "provider", provider.providerName()).increment();
            throw AiProviderFailureClassifier.malformed(exception);
        } catch (RuntimeException exception) {
            timer.stop(meters.timer("stockflow.restock.ai.duration", "provider", provider.providerName(), "status", "error"));
            meters.counter("stockflow.restock.ai.errors", "provider", provider.providerName()).increment();
            throw exception;
        }
    }

    public record Candidate(UUID productId, String sku, String riskLevel, long available,
            long reserved, long reorderPoint, BigDecimal averageDailyUnitsSold, BigDecimal daysOfCover) {}
    public record Selection(List<UUID> productIds, String provider, String model,
            Integer inputTokens, Integer outputTokens, Integer totalTokens,
            BigDecimal estimatedCostUsd, String pricingVersion, long latencyMs) {}
    private record Input(String question, List<Candidate> candidates) {}
    private record ProviderSelection(List<UUID> selectedProductIds) {}
}
