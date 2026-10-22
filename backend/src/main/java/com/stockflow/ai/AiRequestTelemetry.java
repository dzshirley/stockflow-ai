package com.stockflow.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_request_telemetry")
class AiRequestTelemetry {

    enum Status { SUCCEEDED, FAILED }

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false, length = 160)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "estimated_cost_usd", precision = 19, scale = 8)
    private BigDecimal estimatedCostUsd;

    @Column(name = "pricing_version", length = 40)
    private String pricingVersion;

    @Column(name = "tool_call_count", nullable = false)
    private int toolCallCount;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiRequestTelemetry() {
    }

    AiRequestTelemetry(
            UUID id,
            AiModels.InvocationContext invocation,
            String provider,
            String model,
            Status status,
            AiModels.ProviderUsage usage,
            int toolCallCount,
            long durationMs,
            String errorCode,
            Instant createdAt) {
        this.id = id;
        this.requestId = invocation.requestId();
        this.conversationId = invocation.conversationId();
        this.messageId = invocation.messageId();
        this.storeId = invocation.storeContext().storeId();
        this.userId = invocation.storeContext().userId();
        this.provider = provider;
        this.model = model;
        this.status = status;
        this.inputTokens = usage.inputTokens();
        this.outputTokens = usage.outputTokens();
        this.totalTokens = usage.totalTokens();
        this.estimatedCostUsd = usage.estimatedCostUsd();
        this.pricingVersion = usage.pricingVersion();
        this.toolCallCount = toolCallCount;
        this.durationMs = durationMs;
        this.errorCode = errorCode;
        this.createdAt = createdAt;
    }

    UUID getRequestId() { return requestId; }
    String getProvider() { return provider; }
    String getModel() { return model; }
    Status getStatus() { return status; }
    Integer getInputTokens() { return inputTokens; }
    Integer getOutputTokens() { return outputTokens; }
    Integer getTotalTokens() { return totalTokens; }
    BigDecimal getEstimatedCostUsd() { return estimatedCostUsd; }
    int getToolCallCount() { return toolCallCount; }
    long getDurationMs() { return durationMs; }
    String getErrorCode() { return errorCode; }
}
