package com.stockflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockflow.store.StoreContext;
import com.stockflow.store.StoreRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AiRequestTelemetryLoggerTest {

    @Test
    void persistsProviderUsageAndEstimatedCostWithoutPromptContent() {
        var repository = Mockito.mock(AiRequestTelemetryRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var meters = new SimpleMeterRegistry();
        var logger = new AiRequestTelemetryLogger(
                repository, meters, Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC));
        var invocation = invocation();
        var usage = new AiModels.ProviderUsage(100, 25, 125, new BigDecimal("0.00003000"), "2026-10-22");

        logger.success(invocation, new AiModels.ProviderResult("openai", "gpt-4o-mini", "sensitive text", usage), 2, 47);

        var captured = ArgumentCaptor.forClass(AiRequestTelemetry.class);
        verify(repository).save(captured.capture());
        assertThat(captured.getValue().getRequestId()).isEqualTo(invocation.requestId());
        assertThat(captured.getValue().getStatus()).isEqualTo(AiRequestTelemetry.Status.SUCCEEDED);
        assertThat(captured.getValue().getTotalTokens()).isEqualTo(125);
        assertThat(captured.getValue().getEstimatedCostUsd()).isEqualByComparingTo("0.00003000");
        assertThat(captured.getValue().getToolCallCount()).isEqualTo(2);
        assertThat(meters.find("stockflow.ai.request.total_tokens").summary().count()).isEqualTo(1);
        assertThat(AiRequestTelemetry.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("prompt", "question", "response", "apiKey");
    }

    @Test
    void recordsFailureWithoutInventingTokenUsage() {
        var repository = Mockito.mock(AiRequestTelemetryRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var logger = new AiRequestTelemetryLogger(repository, new SimpleMeterRegistry(), Clock.systemUTC());

        logger.failure(invocation(), "openai", "gpt-4o-mini", 0, 201, "AI_PROVIDER_TIMEOUT");

        var captured = ArgumentCaptor.forClass(AiRequestTelemetry.class);
        verify(repository).save(captured.capture());
        assertThat(captured.getValue().getStatus()).isEqualTo(AiRequestTelemetry.Status.FAILED);
        assertThat(captured.getValue().getTotalTokens()).isNull();
        assertThat(captured.getValue().getErrorCode()).isEqualTo("AI_PROVIDER_TIMEOUT");
    }

    private AiModels.InvocationContext invocation() {
        return new AiModels.InvocationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new StoreContext(UUID.randomUUID(), UUID.randomUUID(), StoreRole.ADMIN));
    }
}
