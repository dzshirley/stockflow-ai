package com.stockflow.ai;

import com.stockflow.ai.AiModels.AiResponse;
import com.stockflow.ai.AiModels.InvocationContext;
import com.stockflow.ai.AiModels.ProviderRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class AiChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AiChatOrchestrator.class);
    private final AiProviderGateway provider;
    private final AiExecutionCollector collector;
    private final AiResponseAssembler assembler;
    private final AiRequestTelemetryLogger telemetry;
    private final MeterRegistry meterRegistry;

    AiChatOrchestrator(
            AiProviderGateway provider,
            AiExecutionCollector collector,
            AiResponseAssembler assembler,
            AiRequestTelemetryLogger telemetry,
            MeterRegistry meterRegistry) {
        this.provider = provider;
        this.collector = collector;
        this.assembler = assembler;
        this.telemetry = telemetry;
        this.meterRegistry = meterRegistry;
    }

    AiResponse execute(
            InvocationContext invocation,
            String question,
            java.util.List<AiModels.HistoryMessage> history,
            UUID assistantMessageId) {
        long startedAtNanos = System.nanoTime();
        Timer.Sample timer = Timer.start(meterRegistry);
        collector.begin(invocation.requestId());
        try {
            var providerResult = provider.execute(new ProviderRequest(invocation, question, history));
            var executions = collector.finish(invocation.requestId());
            var response = assembler.assemble(
                    invocation.conversationId(), assistantMessageId, providerResult, executions);
            long durationMs = elapsedMillis(startedAtNanos);
            recordSuccess(invocation, providerResult, executions.size(), durationMs);
            timer.stop(meterRegistry.timer("stockflow.ai.request.duration", "provider", providerResult.provider(), "status", "success"));
            log.info("ai_request_completed requestId={} storeId={} provider={} model={} tools={} inputTokens={} outputTokens={} totalTokens={} estimatedCostUsd={} pricingVersion={} responseType={}",
                    invocation.requestId(), invocation.storeContext().storeId(), providerResult.provider(),
                    providerResult.model(), executions.size(), providerResult.usage().inputTokens(),
                    providerResult.usage().outputTokens(), providerResult.usage().totalTokens(),
                    providerResult.usage().estimatedCostUsd(), providerResult.usage().pricingVersion(),
                    response.responseType());
            return response;
        } catch (RuntimeException exception) {
            int toolCalls = collector.finish(invocation.requestId()).size();
            long durationMs = elapsedMillis(startedAtNanos);
            recordFailure(invocation, toolCalls, durationMs, safeError(exception));
            meterRegistry.counter("stockflow.ai.request.errors", "error", safeError(exception)).increment();
            timer.stop(meterRegistry.timer(
                    "stockflow.ai.request.duration", "provider", provider.providerName(), "status", "error"));
            log.warn("ai_request_failed requestId={} storeId={} provider={} model={} tools={} error={}",
                    invocation.requestId(), invocation.storeContext().storeId(), provider.providerName(),
                    provider.configuredModel(), toolCalls, safeError(exception));
            throw exception;
        }
    }

    private void recordSuccess(
            InvocationContext invocation,
            AiModels.ProviderResult result,
            int toolCalls,
            long durationMs) {
        try {
            telemetry.success(invocation, result, toolCalls, durationMs);
        } catch (RuntimeException exception) {
            meterRegistry.counter("stockflow.ai.telemetry.errors", "operation", "success_record").increment();
            log.warn("ai_telemetry_write_failed requestId={} operation=success_record error={}",
                    invocation.requestId(), exception.getClass().getSimpleName());
        }
    }

    private void recordFailure(
            InvocationContext invocation,
            int toolCalls,
            long durationMs,
            String errorCode) {
        try {
            telemetry.failure(
                    invocation, provider.providerName(), provider.configuredModel(), toolCalls, durationMs, errorCode);
        } catch (RuntimeException exception) {
            meterRegistry.counter("stockflow.ai.telemetry.errors", "operation", "failure_record").increment();
            log.warn("ai_telemetry_write_failed requestId={} operation=failure_record error={}",
                    invocation.requestId(), exception.getClass().getSimpleName());
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000);
    }

    private String safeError(RuntimeException exception) {
        return exception instanceof com.stockflow.common.error.ApiException apiException
                ? apiException.code()
                : exception.getClass().getSimpleName();
    }
}
