package com.stockflow.ai;

import com.stockflow.ai.AiModels.InvocationContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AiToolExecutionLogger {

    private final AiToolExecutionRepository repository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    AiToolExecutionLogger(AiToolExecutionRepository repository, MeterRegistry meterRegistry, Clock clock) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(
            UUID id,
            InvocationContext invocation,
            String toolName,
            AiToolExecution.Status status,
            long durationMs,
            String inputMetadata,
            String errorCode) {
        repository.save(new AiToolExecution(
                id,
                invocation.requestId(),
                invocation.conversationId(),
                invocation.messageId(),
                invocation.storeContext().storeId(),
                invocation.storeContext().userId(),
                toolName,
                status,
                durationMs,
                inputMetadata,
                errorCode,
                Instant.now(clock)));
        meterRegistry.counter(
                "stockflow.ai.tool.executions", "tool", toolName, "status", status.name()).increment();
        meterRegistry.timer("stockflow.ai.tool.duration", "tool", toolName).record(
                java.time.Duration.ofMillis(durationMs));
    }
}
