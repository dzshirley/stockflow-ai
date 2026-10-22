package com.stockflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockflow.common.error.ApiException;
import com.stockflow.store.StoreContext;
import com.stockflow.store.StoreRole;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "stockflow.ai.provider=openai",
        "spring.ai.model.chat=openai",
        "spring.ai.openai.api-key=test-key-not-a-secret",
        "spring.ai.openai.chat.model=gpt-4o-mini",
        "spring.ai.openai.timeout=200ms",
        "spring.ai.openai.max-retries=0",
        "stockflow.ai.resilience.max-attempts=2",
        "stockflow.ai.resilience.initial-backoff=10ms",
        "stockflow.ai.resilience.backoff-multiplier=1",
        "stockflow.ai.resilience.max-backoff=10ms"
})
@Timeout(10)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SpringAiProviderFaultTest {

    private static final MockWebServer SERVER = startServer();

    @Autowired
    private AiProviderGateway provider;

    @AfterAll
    static void stopServer() throws IOException {
        SERVER.shutdown();
    }

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> SERVER.url("/").toString());
    }

    @Test
    @Order(1)
    void retriesRateLimitOnceAndThenSucceeds() {
        int before = SERVER.getRequestCount();
        SERVER.enqueue(jsonResponse(429, "{\"error\":{\"message\":\"rate limited\"}}"));
        SERVER.enqueue(successResponse());

        var result = provider.execute(request());

        assertThat(result.text()).isEqualTo("Verified response");
        assertThat(result.usage().totalTokens()).isEqualTo(15);
        assertThat(SERVER.getRequestCount() - before).isEqualTo(2);
    }

    @ParameterizedTest
    @Order(2)
    @ValueSource(ints = {500, 502, 503})
    void retriesTransientServerErrorsWithinBound(int status) {
        int before = SERVER.getRequestCount();
        SERVER.enqueue(jsonResponse(status, "{\"error\":{\"message\":\"temporary\"}}"));
        SERVER.enqueue(successResponse());

        assertThat(provider.execute(request()).text()).isEqualTo("Verified response");
        assertThat(SERVER.getRequestCount() - before).isEqualTo(2);
    }

    @Test
    @Order(3)
    void retriesConnectionResetWithoutDuplicatingToolOperations() {
        int before = SERVER.getRequestCount();
        SERVER.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        SERVER.enqueue(successResponse());

        var result = provider.execute(request());

        assertThat(result.text()).isEqualTo("Verified response");
        assertThat(SERVER.getRequestCount() - before).isEqualTo(2);
    }

    @Test
    @Order(6)
    void timeoutIsBoundedAndHasSafeError() {
        int before = SERVER.getRequestCount();
        SERVER.enqueue(successResponse().setHeadersDelay(1, TimeUnit.SECONDS));
        SERVER.enqueue(successResponse().setHeadersDelay(1, TimeUnit.SECONDS));
        long started = System.nanoTime();

        assertThatThrownBy(() -> provider.execute(request()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("AI_PROVIDER_TIMEOUT");
                    assertThat(exception.getMessage()).doesNotContain("test-key");
                });

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
        assertThat(SERVER.getRequestCount() - before).isEqualTo(2);
    }

    @Test
    @Order(7)
    void businessSynthesisUsesTheSameBoundedTransientRetryPolicy() {
        int before = SERVER.getRequestCount();
        SERVER.enqueue(jsonResponse(503, "{\"error\":{\"message\":\"temporary\"}}"));
        SERVER.enqueue(businessSelectionResponse());

        var result = provider.synthesize(new AiModels.BusinessSynthesisRequest("{\"revenueDirection\":-1}"));

        assertThat(result.text()).contains("REVIEW_REVENUE_DRIVERS");
        assertThat(result.usage().totalTokens()).isEqualTo(15);
        assertThat(SERVER.getRequestCount() - before).isEqualTo(2);
    }

    @Test
    @Order(4)
    void malformedResponseFailsClearlyWithoutInfiniteRetry() {
        int before = SERVER.getRequestCount();
        SERVER.enqueue(jsonResponse(200, "{not-json"));

        assertThatThrownBy(() -> provider.execute(request()))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AI_PROVIDER_MALFORMED_RESPONSE"));
        assertThat(SERVER.getRequestCount() - before).isBetween(1, 2);
    }

    @Test
    @Order(5)
    void interruptedResponseFailsWithinRetryLimit() {
        int before = SERVER.getRequestCount();
        String body = successBody();
        SERVER.enqueue(jsonResponse(200, body).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        SERVER.enqueue(jsonResponse(200, body).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));

        assertThatThrownBy(() -> provider.execute(request()))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).startsWith("AI_PROVIDER_"));
        assertThat(SERVER.getRequestCount() - before).isBetween(1, 2);
    }

    private AiModels.ProviderRequest request() {
        var context = new StoreContext(UUID.randomUUID(), UUID.randomUUID(), StoreRole.ADMIN);
        var invocation = new AiModels.InvocationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), context);
        return new AiModels.ProviderRequest(invocation, "Summarize revenue", List.of());
    }

    private static MockResponse successResponse() {
        return jsonResponse(200, successBody());
    }

    private static MockResponse businessSelectionResponse() {
        return jsonResponse(200, """
                {
                  "id":"chatcmpl-business-test",
                  "object":"chat.completion",
                  "created":1792641600,
                  "model":"gpt-4o-mini",
                  "choices":[{"index":0,"message":{"role":"assistant","content":"{\\"hypothesisCodes\\":[],\\"recommendationCodes\\":[\\"REVIEW_REVENUE_DRIVERS\\"]}"},"finish_reason":"stop"}],
                  "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}
                }
                """);
    }

    private static MockResponse jsonResponse(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static String successBody() {
        return """
                {
                  "id":"chatcmpl-test",
                  "object":"chat.completion",
                  "created":1792641600,
                  "model":"gpt-4o-mini",
                  "choices":[{"index":0,"message":{"role":"assistant","content":"Verified response"},"finish_reason":"stop"}],
                  "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}
                }
                """;
    }

    private static MockWebServer startServer() {
        var server = new MockWebServer();
        try {
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
