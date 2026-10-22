package com.stockflow.ai;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import org.springframework.http.HttpStatus;

final class AiProviderFailureClassifier {

    private AiProviderFailureClassifier() {
    }

    static AiProviderException translate(RuntimeException exception) {
        if (exception instanceof AiProviderException providerException) return providerException;
        String description = describe(exception);
        if (hasCause(exception, SocketTimeoutException.class)
                || hasCause(exception, HttpTimeoutException.class)
                || description.contains("timeout")
                || description.contains("timed out")) {
            return new AiProviderException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "AI_PROVIDER_TIMEOUT",
                    "The analytics provider timed out; retry the request later",
                    exception);
        }
        if (description.contains("429") || description.contains("rate limit")) {
            return new AiProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_PROVIDER_RATE_LIMITED",
                    "The analytics provider is temporarily rate limited; retry the request later",
                    exception);
        }
        if (description.contains("json") || description.contains("malformed")
                || description.contains("parse") || description.contains("decode")) {
            return malformed(exception);
        }
        return new AiProviderException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_PROVIDER_UNAVAILABLE",
                "The analytics provider is temporarily unavailable",
                exception);
    }

    static AiProviderException malformed(Throwable cause) {
        return new AiProviderException(
                HttpStatus.BAD_GATEWAY,
                "AI_PROVIDER_MALFORMED_RESPONSE",
                "The analytics provider returned an invalid response",
                cause);
    }

    static boolean isTransient(RuntimeException exception) {
        String description = describe(exception);
        if (description.contains("connection reset")
                || description.contains("connection refused")
                || description.contains("broken pipe")
                || description.contains("unexpected end of stream")) {
            return true;
        }
        if (description.contains("jsonparse") || description.contains("malformed")
                || description.contains("decode")) {
            return false;
        }
        return hasCause(exception, java.io.IOException.class)
                || hasCause(exception, SocketTimeoutException.class)
                || hasCause(exception, HttpTimeoutException.class)
                || description.contains("timeout")
                || description.contains("timed out")
                || description.contains("429")
                || description.contains("rate limit")
                || description.contains("500")
                || description.contains("502")
                || description.contains("503")
                || description.contains("504");
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    private static String describe(Throwable throwable) {
        var description = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            description.append(current.getClass().getSimpleName()).append(' ');
            if (current.getMessage() != null) description.append(current.getMessage()).append(' ');
        }
        return description.toString().toLowerCase(Locale.ROOT);
    }
}
