package com.stockflow.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockflow.common.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptGuardTest {

    @Test
    void blocksOnlyAfterTheConfiguredFailuresAndClearsAfterSuccess() {
        var clock = new MutableClock(Instant.parse("2026-10-23T12:00:00Z"));
        var guard = new LoginAttemptGuard(clock, 3, Duration.ofMinutes(15), 100);

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatCode(() -> guard.checkAllowed("Owner@Example.com", "127.0.0.1"))
                    .doesNotThrowAnyException();
            guard.recordFailure("owner@example.com", "127.0.0.1");
        }

        assertThatThrownBy(() -> guard.checkAllowed("owner@example.com", "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    var api = (ApiException) error;
                    org.assertj.core.api.Assertions.assertThat(api.status().value()).isEqualTo(429);
                    org.assertj.core.api.Assertions.assertThat(api.code()).isEqualTo("AUTH_RATE_LIMITED");
                });

        guard.recordSuccess("owner@example.com", "127.0.0.1");
        assertThatCode(() -> guard.checkAllowed("owner@example.com", "127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void expiresFailuresAndSeparatesRemoteAddress() {
        var clock = new MutableClock(Instant.parse("2026-10-23T12:00:00Z"));
        var guard = new LoginAttemptGuard(clock, 1, Duration.ofMinutes(15), 100);
        guard.recordFailure("owner@example.com", "10.0.0.1");

        assertThatCode(() -> guard.checkAllowed("owner@example.com", "10.0.0.2"))
                .doesNotThrowAnyException();
        clock.advance(Duration.ofMinutes(16));
        assertThatCode(() -> guard.checkAllowed("owner@example.com", "10.0.0.1"))
                .doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
