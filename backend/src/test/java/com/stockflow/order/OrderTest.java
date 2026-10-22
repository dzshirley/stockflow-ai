package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockflow.common.error.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {

    private final Instant now = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void allowsPendingOrderToBecomePaid() {
        var order = pendingOrder();

        order.markPaid(now.plusSeconds(5));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getUpdatedAt()).isEqualTo(now.plusSeconds(5));
        assertThat(order.getPaidAt()).isEqualTo(now.plusSeconds(5));
    }

    @Test
    void rejectsInvalidTerminalTransition() {
        var order = pendingOrder();
        order.cancel(now.plusSeconds(5));

        assertThatThrownBy(() -> order.markPaid(now.plusSeconds(10)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Only pending orders can be paid");
    }

    @Test
    void supportsCancellationAndExpirationAsDistinctTerminalStates() {
        var cancelled = pendingOrder();
        var expired = pendingOrder();

        cancelled.cancel(now.plusSeconds(5));
        expired.expire(now.plusSeconds(901));

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(expired.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    private Order pendingOrder() {
        return new Order(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10.0000"),
                "USD",
                now.plusSeconds(900),
                UUID.randomUUID(),
                now);
    }
}
