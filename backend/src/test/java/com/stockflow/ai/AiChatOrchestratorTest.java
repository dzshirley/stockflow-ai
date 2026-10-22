package com.stockflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stockflow.store.StoreContext;
import com.stockflow.store.StoreRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiChatOrchestratorTest {

    @Test
    void telemetryFailureDoesNotReplaceSuccessfulProviderResponse() {
        var provider = mock(AiProviderGateway.class);
        when(provider.execute(any())).thenReturn(new AiModels.ProviderResult(
                "mock", "deterministic-v1", "response", AiModels.ProviderUsage.unavailable()));
        var telemetry = mock(AiRequestTelemetryLogger.class);
        doThrow(new RuntimeException("telemetry database unavailable"))
                .when(telemetry).success(any(), any(), anyInt(), anyLong());
        var orchestrator = new AiChatOrchestrator(
                provider, new AiExecutionCollector(), new AiResponseAssembler(), telemetry,
                new SimpleMeterRegistry());
        var invocation = new AiModels.InvocationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new StoreContext(UUID.randomUUID(), UUID.randomUUID(), StoreRole.ADMIN));

        var response = orchestrator.execute(
                invocation, "Show revenue", List.of(), UUID.randomUUID());

        assertThat(response.responseType()).isEqualTo(AiModels.ResponseType.CLARIFICATION);
        assertThat(response.provider()).isEqualTo("mock");
    }
}
