package com.stockflow.order;

import com.stockflow.common.error.ApiExceptions;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderIdempotencyGate {

    private final JdbcTemplate jdbcTemplate;

    public OrderIdempotencyGate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Gate acquire(UUID storeId, String key, String requestHash, Instant now) {
        jdbcTemplate.update(
                """
                INSERT INTO order_idempotency_keys
                    (id, store_id, idempotency_key, request_hash, order_id, created_at)
                VALUES (?, ?, ?, ?, NULL, ?)
                ON CONFLICT (store_id, idempotency_key) DO NOTHING
                """,
                UUID.randomUUID(), storeId, key, requestHash, Timestamp.from(now));

        var gate = jdbcTemplate.queryForObject(
                """
                SELECT id, request_hash, order_id
                FROM order_idempotency_keys
                WHERE store_id = ? AND idempotency_key = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> new Gate(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("request_hash"),
                        resultSet.getObject("order_id", UUID.class)),
                storeId,
                key);
        if (gate == null) {
            throw ApiExceptions.conflict("IDEMPOTENCY_CONFLICT", "Idempotency key could not be acquired");
        }
        if (!gate.requestHash().equals(requestHash)) {
            throw ApiExceptions.conflict(
                    "IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was already used for a different request");
        }
        return gate;
    }

    public void complete(UUID gateId, UUID orderId) {
        if (jdbcTemplate.update(
                        "UPDATE order_idempotency_keys SET order_id = ? WHERE id = ? AND order_id IS NULL",
                        orderId,
                        gateId)
                != 1) {
            throw ApiExceptions.conflict("IDEMPOTENCY_CONFLICT", "Idempotency key was already completed");
        }
    }

    public record Gate(UUID id, String requestHash, UUID orderId) {
    }
}
