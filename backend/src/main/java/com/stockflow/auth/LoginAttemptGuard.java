package com.stockflow.auth;

import com.stockflow.common.error.ApiExceptions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bounded local login-failure protection. The edge proxy remains responsible for
 * distributed/IP-wide rate limiting when more than one application instance is deployed.
 */
@Component
class LoginAttemptGuard {

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final int maxTrackedKeys;
    private final Map<String, AttemptWindow> failures = new LinkedHashMap<>();

    LoginAttemptGuard(
            Clock clock,
            @Value("${stockflow.security.login.max-failures:5}") int maxFailures,
            @Value("${stockflow.security.login.window:15m}") Duration window,
            @Value("${stockflow.security.login.max-tracked-keys:10000}") int maxTrackedKeys) {
        if (maxFailures < 1 || window.isNegative() || window.isZero() || maxTrackedKeys < 1) {
            throw new IllegalArgumentException("Login protection settings must be positive");
        }
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
        this.maxTrackedKeys = maxTrackedKeys;
    }

    synchronized void checkAllowed(String email, String remoteAddress) {
        var now = Instant.now(clock);
        evictExpired(now);
        var attempt = failures.get(key(email, remoteAddress));
        if (attempt != null && attempt.failures() >= maxFailures) {
            throw ApiExceptions.tooManyRequests(
                    "AUTH_RATE_LIMITED", "Too many failed login attempts; retry later");
        }
    }

    synchronized void recordFailure(String email, String remoteAddress) {
        var now = Instant.now(clock);
        evictExpired(now);
        var key = key(email, remoteAddress);
        var current = failures.get(key);
        if (current == null) {
            ensureCapacity();
            failures.put(key, new AttemptWindow(1, now));
        } else {
            failures.put(key, new AttemptWindow(current.failures() + 1, current.startedAt()));
        }
    }

    synchronized void recordSuccess(String email, String remoteAddress) {
        failures.remove(key(email, remoteAddress));
    }

    private void evictExpired(Instant now) {
        failures.entrySet().removeIf(entry -> !entry.getValue().startedAt().plus(window).isAfter(now));
    }

    private void ensureCapacity() {
        while (failures.size() >= maxTrackedKeys) {
            Iterator<String> iterator = failures.keySet().iterator();
            if (!iterator.hasNext()) return;
            iterator.next();
            iterator.remove();
        }
    }

    private String key(String email, String remoteAddress) {
        var normalized = email.trim().toLowerCase(Locale.ROOT) + "\u0000"
                + (remoteAddress == null ? "unknown" : remoteAddress);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record AttemptWindow(int failures, Instant startedAt) {}
}
