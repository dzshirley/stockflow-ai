package com.stockflow.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_tool_executions")
class AiToolExecution {

    enum Status { SUCCEEDED, FAILED }

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tool_name", nullable = false, length = 80)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "input_metadata", nullable = false, columnDefinition = "TEXT")
    private String inputMetadata;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiToolExecution() {
    }

    AiToolExecution(
            UUID id,
            UUID requestId,
            UUID conversationId,
            UUID messageId,
            UUID storeId,
            UUID userId,
            String toolName,
            Status status,
            long durationMs,
            String inputMetadata,
            String errorCode,
            Instant createdAt) {
        this.id = id;
        this.requestId = requestId;
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.storeId = storeId;
        this.userId = userId;
        this.toolName = toolName;
        this.status = status;
        this.durationMs = durationMs;
        this.inputMetadata = inputMetadata;
        this.errorCode = errorCode;
        this.createdAt = createdAt;
    }

    UUID getId() { return id; }
    String getToolName() { return toolName; }
    Status getStatus() { return status; }
    long getDurationMs() { return durationMs; }
}
