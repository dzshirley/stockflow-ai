package com.stockflow.ai;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

@Component
class AiExecutionCollector {

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<AiModels.ToolEnvelope>> executions =
            new ConcurrentHashMap<>();

    void begin(UUID requestId) {
        executions.put(requestId, new CopyOnWriteArrayList<>());
    }

    void add(UUID requestId, AiModels.ToolEnvelope result) {
        var bucket = executions.get(requestId);
        if (bucket == null) throw new IllegalStateException("AI execution scope is not active");
        bucket.add(result);
    }

    List<AiModels.ToolEnvelope> finish(UUID requestId) {
        var results = executions.remove(requestId);
        return results == null ? List.of() : List.copyOf(results);
    }

    void discard(UUID requestId) {
        executions.remove(requestId);
    }

    void restart(UUID requestId) {
        executions.put(requestId, new CopyOnWriteArrayList<>());
    }
}
