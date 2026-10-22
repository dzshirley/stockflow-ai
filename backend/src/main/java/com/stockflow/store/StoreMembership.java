package com.stockflow.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_memberships")
public class StoreMembership {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoreRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StoreMembership() {
    }

    public StoreMembership(UUID id, UUID storeId, UUID userId, StoreRole role, Instant createdAt) {
        this.id = id;
        this.storeId = storeId;
        this.userId = userId;
        this.role = role;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getStoreId() { return storeId; }
    public UUID getUserId() { return userId; }
    public StoreRole getRole() { return role; }
}
