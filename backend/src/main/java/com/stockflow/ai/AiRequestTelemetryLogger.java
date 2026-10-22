package com.stockflow.ai;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AiRequestTelemetryLogger {

    private final AiRequestTelemetryRepository repository;
    private final MeterRegistry meters;
    private final Clock clock;

    AiRequestTelemetryLogger(AiRequestTelemetryRepository repository, MeterRegistry meters, Clock clock) {
        this.repository = repository;
        this.meters = meters;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void success(
            AiModels.InvocationContext invocation,
            AiModels.ProviderResult result,
            int toolCalls,
            long durationMs) {
        record(invocation, result.provider(), result.model(), AiRequestTelemetry.Status.SUCCEEDED,
                result.usage(), toolCalls, durationMs, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void failure(
            AiModels.InvocationContext invocation,
            String provider,
            String model,
            int toolCalls,
            long durationMs,
            String errorCode) {
        record(invocation, provider, model, AiRequestTelemetry.Status.FAILED,
                AiModels.ProviderUsage.unavailable(), toolCalls, durationMs, errorCode);
    }

    private void record(
            AiModels.InvocationContext invocation,
            String provider,
            String model,
            AiRequestTelemetry.Status status,
            AiModels.ProviderUsage usage,
            int toolCalls,
            long durationMs,
            String errorCode) {
        repository.save(new AiRequestTelemetry(
                UUID.randomUUID(), invocation, provider, model, status, usage,
                toolCalls, durationMs, errorCode, Instant.now(clock)));

        String statusTag = status.name().toLowerCase();
        meters.timer("stockflow.ai.request.provider_duration", "provider", provider, "status", statusTag)
                .record(Duration.ofMillis(durationMs));
        meters.summary("stockflow.ai.request.tool_count", "provider", provider, "status", statusTag)
                .record(toolCalls);
        if (usage.inputTokens() != null) {
            meters.summary("stockflow.ai.request.input_tokens", "provider", provider, "model", model)
                    .record(usage.inputTokens());
            meters.summary("stockflow.ai.request.output_tokens", "provider", provider, "model", model)
                    .record(usage.outputTokens());
            meters.summary("stockflow.ai.request.total_tokens", "provider", provider, "model", model)
                    .record(usage.totalTokens());
        }
        if (usage.estimatedCostUsd() != null) {
            meters.summary("stockflow.ai.request.estimated_cost_usd", "provider", provider, "model", model)
                    .record(usage.estimatedCostUsd().doubleValue());
        }
    }
}
