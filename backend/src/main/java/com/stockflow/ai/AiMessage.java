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
@Table(name = "ai_messages")
class AiMessage {

    enum Role { USER, ASSISTANT }

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiMessage() {
    }

    AiMessage(
            UUID id,
            UUID conversationId,
            UUID storeId,
            Role role,
            String content,
            String responsePayload,
            Instant createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.storeId = storeId;
        this.role = role;
        this.content = content;
        this.responsePayload = responsePayload;
        this.createdAt = createdAt;
    }

    UUID getId() { return id; }
    UUID getConversationId() { return conversationId; }
    AiMessage.Role getRole() { return role; }
    String getContent() { return content; }
    String getResponsePayload() { return responsePayload; }
    Instant getCreatedAt() { return createdAt; }
}
