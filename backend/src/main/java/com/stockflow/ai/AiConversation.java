package com.stockflow.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_conversations")
class AiConversation {

    enum Status { ACTIVE, ARCHIVED }

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 160)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AiConversation() {
    }

    AiConversation(UUID id, UUID storeId, UUID userId, String title, Instant now) {
        this.id = id;
        this.storeId = storeId;
        this.userId = userId;
        this.title = title;
        this.status = Status.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void touch(Instant now) {
        updatedAt = now;
    }

    void setTitle(String title, Instant now) {
        this.title = title;
        this.updatedAt = now;
    }

    UUID getId() { return id; }
    UUID getStoreId() { return storeId; }
    UUID getUserId() { return userId; }
    String getTitle() { return title; }
    Status getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
