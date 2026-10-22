package com.stockflow.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stores")
public class Store {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Store() {
    }

    public Store(UUID id, String name, String currencyCode, String timeZone, Instant now) {
        this.id = id;
        this.name = name;
        this.currencyCode = currencyCode;
        this.timeZone = timeZone;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCurrencyCode() { return currencyCode; }
    public String getTimeZone() { return timeZone; }
}
