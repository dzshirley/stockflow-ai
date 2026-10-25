package com.stockflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockflow.auth.AuthModels;
import com.stockflow.auth.AuthenticationService;
import com.stockflow.ai.AiAnalyticsService;
import com.stockflow.ai.AiConversationService;
import com.stockflow.ai.AiModels;
import com.stockflow.analytics.AnalyticsModels;
import com.stockflow.analytics.AnalyticsService;
import com.stockflow.common.error.ApiException;
import com.stockflow.inventory.InventoryService;
import com.stockflow.order.OrderModels;
import com.stockflow.order.OrderService;
import com.stockflow.order.OrderStatus;
import com.stockflow.product.ProductModels;
import com.stockflow.product.ProductService;
import com.stockflow.store.StoreContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "stockflow.orders.expiration-delay=PT24H")
@Testcontainers
@Timeout(30)
class CommerceWorkflowIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("stockflow_test")
            .withUsername("stockflow")
            .withPassword("stockflow_test")
            .withStartupTimeout(Duration.ofSeconds(30))
            .withCommand("postgres", "-c", "lock_timeout=5s", "-c", "statement_timeout=30s");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired AuthenticationService authenticationService;
    @Autowired ProductService productService;
    @Autowired InventoryService inventoryService;
    @Autowired OrderService orderService;
    @Autowired AnalyticsService analyticsService;
    @Autowired AiAnalyticsService aiAnalyticsService;
    @Autowired AiConversationService aiConversationService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE app_users, stores CASCADE");
    }

    @Test
    void isolatesProductsAcrossStores() {
        var first = tenant("first@example.com", "First");
        var second = tenant("second@example.com", "Second");
        var product = product(first, "ONE", 3);

        assertThatThrownBy(() -> productService.get(second, product.id()))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).status().value())
                .isEqualTo(404);
    }

    @Test
    void searchesProductsWithAbsentOptionalFiltersOnPostgres() {
        var tenant = tenant("search@example.com", "Search");
        product(tenant, "SEARCHABLE", 3);

        var result = productService.search(
                tenant, null, null, com.stockflow.product.ProductStatus.ACTIVE,
                0, 20, "name", org.springframework.data.domain.Sort.Direction.ASC);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items()).extracting(ProductModels.Response::sku).containsExactly("SEARCHABLE");
    }

    @Test
    void allowsExactlyOneConcurrentReservationOfLastItem() throws Exception {
        var tenant = tenant("concurrency@example.com", "Concurrency");
        var product = product(tenant, "LAST", 1);
        var start = new CountDownLatch(1);
        var transaction = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var reserve = (java.util.concurrent.Callable<Boolean>) () -> {
                start.await();
                try {
                    transaction.executeWithoutResult(status -> inventoryService.reserve(
                            tenant.storeId(), product.id(), 1, tenant.userId(), "CONCURRENCY_TEST"));
                    return true;
                } catch (ApiException exception) {
                    return false;
                }
            };
            var first = executor.submit(reserve);
            var second = executor.submit(reserve);
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        var balance = inventoryService.get(tenant, product.id());
        assertThat(balance.availableQuantity()).isZero();
        assertThat(balance.reservedQuantity()).isEqualTo(1);
    }

    @Test
    void concurrentSameKeyOrderCreationCreatesOneOrderAndOneReservation() throws Exception {
        var tenant = tenant("same-key-race@example.com", "Same key race");
        var product = product(tenant, "SAME-KEY", 5);
        var request = new OrderModels.CreateRequest(List.of(new OrderModels.LineRequest(product.id(), 1)));
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var create = (java.util.concurrent.Callable<UUID>) () -> {
                start.await();
                return orderService.create(tenant, "concurrent-same-key", request).id();
            };
            var first = executor.submit(create);
            var second = executor.submit(create);
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS))
                    .isEqualTo(second.get(10, TimeUnit.SECONDS));
        }

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_idempotency_keys", Long.class)).isEqualTo(1);
        var balance = inventoryService.get(tenant, product.id());
        assertThat(balance.availableQuantity()).isEqualTo(4);
        assertThat(balance.reservedQuantity()).isEqualTo(1);
    }

    @Test
    void concurrentPaymentAndCancellationCommitExactlyOneTerminalOutcome() throws Exception {
        var tenant = tenant("terminal-race@example.com", "Terminal race");
        var product = product(tenant, "TERMINAL", 5);
        var order = orderService.create(tenant, "terminal-race-key", new OrderModels.CreateRequest(
                List.of(new OrderModels.LineRequest(product.id(), 2))));
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var payment = executor.submit(() -> {
                start.await();
                try {
                    return orderService.payment(tenant, order.id(), OrderModels.PaymentOutcome.APPROVED)
                            .status().name();
                } catch (ApiException exception) {
                    return exception.code();
                }
            });
            var cancellation = executor.submit(() -> {
                start.await();
                try {
                    return orderService.cancel(tenant, order.id()).status().name();
                } catch (ApiException exception) {
                    return exception.code();
                }
            });
            start.countDown();

            var results = List.of(
                    payment.get(10, TimeUnit.SECONDS), cancellation.get(10, TimeUnit.SECONDS));
            assertThat(results).contains("INVALID_ORDER_TRANSITION");
            assertThat(results.stream().filter(value -> value.equals("PAID") || value.equals("CANCELLED")))
                    .hasSize(1);
        }

        var finalOrder = orderService.get(tenant, order.id());
        assertThat(finalOrder.status()).isIn(OrderStatus.PAID, OrderStatus.CANCELLED);
        var balance = inventoryService.get(tenant, product.id());
        assertThat(balance.reservedQuantity()).isZero();
        assertThat(balance.availableQuantity())
                .isEqualTo(finalOrder.status() == OrderStatus.PAID ? 3 : 5);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM outbox_events
                WHERE aggregate_id = ? AND event_type IN ('ORDER_PAID', 'ORDER_CANCELLED')
                """, Long.class, order.id())).isEqualTo(1);
    }

    @Test
    void replaysIdempotentOrderAndAppliesPaymentOnce() {
        var tenant = tenant("orders@example.com", "Orders");
        var product = product(tenant, "ORDERED", 5);
        var request = new OrderModels.CreateRequest(List.of(new OrderModels.LineRequest(product.id(), 2)));

        var created = orderService.create(tenant, "same-order-key", request);
        var replayed = orderService.create(tenant, "same-order-key", request);

        assertThat(replayed.id()).isEqualTo(created.id());
        assertThatThrownBy(() -> orderService.create(
                        tenant,
                        "same-order-key",
                        new OrderModels.CreateRequest(List.of(new OrderModels.LineRequest(product.id(), 1)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different request");
        var reserved = inventoryService.get(tenant, product.id());
        assertThat(reserved.availableQuantity()).isEqualTo(3);
        assertThat(reserved.reservedQuantity()).isEqualTo(2);

        var paid = orderService.payment(tenant, created.id(), OrderModels.PaymentOutcome.APPROVED);
        var paidReplay = orderService.payment(tenant, created.id(), OrderModels.PaymentOutcome.APPROVED);
        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
        assertThat(paidReplay.status()).isEqualTo(OrderStatus.PAID);
        var afterPayment = inventoryService.get(tenant, product.id());
        assertThat(afterPayment.availableQuantity()).isEqualTo(3);
        assertThat(afterPayment.reservedQuantity()).isZero();
    }

    @Test
    void cancellationReleasesReservedInventoryExactlyOnce() {
        var tenant = tenant("cancel@example.com", "Cancellation");
        var product = product(tenant, "CANCELLED", 3);
        var request = new OrderModels.CreateRequest(List.of(new OrderModels.LineRequest(product.id(), 2)));
        var created = orderService.create(tenant, "cancel-key", request);

        var cancelled = orderService.cancel(tenant, created.id());
        var replayed = orderService.cancel(tenant, created.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(replayed.status()).isEqualTo(OrderStatus.CANCELLED);
        var balance = inventoryService.get(tenant, product.id());
        assertThat(balance.availableQuantity()).isEqualTo(3);
        assertThat(balance.reservedQuantity()).isZero();
    }

    @Test
    void rollsBackEarlierReservationsWhenALaterLineIsUnavailable() {
        var tenant = tenant("rollback@example.com", "Rollback");
        var one = product(tenant, "ROLL-A", 1);
        var two = product(tenant, "ROLL-B", 1);
        var sorted = List.of(one, two).stream()
                .sorted(java.util.Comparator.comparing(product -> product.id().toString()))
                .toList();
        inventoryService.adjust(
                tenant,
                sorted.get(1).id(),
                new com.stockflow.inventory.InventoryModels.AdjustmentRequest(-1L, null, "SETUP"));
        var request = new OrderModels.CreateRequest(List.of(
                new OrderModels.LineRequest(sorted.get(0).id(), 1),
                new OrderModels.LineRequest(sorted.get(1).id(), 1)));

        assertThatThrownBy(() -> orderService.create(tenant, "rollback-key", request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Insufficient inventory");

        var firstBalance = inventoryService.get(tenant, sorted.get(0).id());
        assertThat(firstBalance.availableQuantity()).isEqualTo(1);
        assertThat(firstBalance.reservedQuantity()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_idempotency_keys", Long.class)).isZero();
    }

    @Test
    void aggregatesPaidOrdersWithoutJoinInflationAndKeepsTenantsIsolated() {
        var tenant = tenant("analytics@example.com", "Analytics");
        var otherTenant = tenant("analytics-other@example.com", "Other analytics");
        var first = product(tenant, "AN-A", 20);
        var second = product(tenant, "AN-B", 20);
        var other = product(otherTenant, "AN-OTHER", 20);

        var paid = orderService.create(tenant, "analytics-paid", new OrderModels.CreateRequest(List.of(
                new OrderModels.LineRequest(first.id(), 2),
                new OrderModels.LineRequest(second.id(), 1))));
        orderService.payment(tenant, paid.id(), OrderModels.PaymentOutcome.APPROVED);
        var cancelled = orderService.create(tenant, "analytics-cancelled", new OrderModels.CreateRequest(
                List.of(new OrderModels.LineRequest(first.id(), 5))));
        orderService.cancel(tenant, cancelled.id());
        var otherPaid = orderService.create(otherTenant, "analytics-other", new OrderModels.CreateRequest(
                List.of(new OrderModels.LineRequest(other.id(), 7))));
        orderService.payment(otherTenant, otherPaid.id(), OrderModels.PaymentOutcome.APPROVED);

        var overview = analyticsService.getRevenueSummary(
                tenant, AnalyticsModels.PeriodPreset.LAST_30_DAYS, null, null, true);
        assertThat(overview.metrics().revenue()).isEqualByComparingTo("30.0000");
        assertThat(overview.metrics().orders()).isEqualTo(1);
        assertThat(overview.metrics().averageOrderValue()).isEqualByComparingTo("30.0000");
        assertThat(overview.metrics().unitsSold()).isEqualTo(3);
        assertThat(overview.comparison().comparable()).isFalse();
        assertThat(overview.comparison().revenueGrowthPercent()).isNull();

        var products = analyticsService.getTopProducts(
                tenant, AnalyticsModels.PeriodPreset.LAST_30_DAYS, null, null, 10);
        assertThat(products.items()).hasSize(2);
        assertThat(products.items().getFirst().sku()).isEqualTo("AN-A");
        assertThat(products.items().getFirst().revenue()).isEqualByComparingTo("20.0000");

        var categories = analyticsService.getSalesByCategory(
                tenant, AnalyticsModels.PeriodPreset.LAST_30_DAYS, null, null);
        assertThat(categories.items()).singleElement()
                .satisfies(category -> {
                    assertThat(category.category()).isEqualTo("General");
                    assertThat(category.revenue()).isEqualByComparingTo("30.0000");
                    assertThat(category.orders()).isEqualTo(1);
                });

        var inventory = analyticsService.getInventorySummary(
                tenant, AnalyticsModels.PeriodPreset.LAST_30_DAYS, null, null);
        assertThat(inventory.activeProducts()).isEqualTo(2);
        assertThat(inventory.availableUnits()).isEqualTo(37);
        assertThat(inventory.reservedUnits()).isZero();
        assertThat(inventory.unitsSoldInPeriod()).isEqualTo(3);
    }

    @Test
    void usesInclusiveStartAndExclusiveEndPaymentBoundaries() {
        var tenant = tenant("boundaries@example.com", "Boundaries");
        var product = product(tenant, "BOUNDARY", 10);
        var included = orderService.create(tenant, "boundary-included", new OrderModels.CreateRequest(
                List.of(new OrderModels.LineRequest(product.id(), 1))));
        var excluded = orderService.create(tenant, "boundary-excluded", new OrderModels.CreateRequest(
                List.of(new OrderModels.LineRequest(product.id(), 1))));
        orderService.payment(tenant, included.id(), OrderModels.PaymentOutcome.APPROVED);
        orderService.payment(tenant, excluded.id(), OrderModels.PaymentOutcome.APPROVED);
        jdbcTemplate.update("UPDATE orders SET paid_at = ? WHERE id = ?",
                java.sql.Timestamp.from(java.time.Instant.parse("2026-01-01T05:00:00Z")), included.id());
        jdbcTemplate.update("UPDATE orders SET paid_at = ? WHERE id = ?",
                java.sql.Timestamp.from(java.time.Instant.parse("2026-01-03T05:00:00Z")), excluded.id());

        var result = analyticsService.getRevenueSummary(
                tenant,
                AnalyticsModels.PeriodPreset.CUSTOM,
                java.time.LocalDate.parse("2026-01-01"),
                java.time.LocalDate.parse("2026-01-03"),
                false);

        assertThat(result.metrics().orders()).isEqualTo(1);
        assertThat(result.metrics().revenue()).isEqualByComparingTo("10.0000");
    }

    @Test
    void answersWithVerifiedToolsPersistsConversationAndEnforcesOwnership() {
        var tenant = tenant("ai@example.com", "AI store");
        var otherTenant = tenant("ai-other@example.com", "Other AI store");
        var product = product(tenant, "AI-SKU", 10);
        var order = orderService.create(tenant, "ai-order", new OrderModels.CreateRequest(
                List.of(new OrderModels.LineRequest(product.id(), 2))));
        orderService.payment(tenant, order.id(), OrderModels.PaymentOutcome.APPROVED);

        var first = aiAnalyticsService.query(
                tenant, new AiModels.QueryRequest(null, "Show revenue for the last 30 days"));
        var followUp = aiAnalyticsService.query(
                tenant, new AiModels.QueryRequest(first.conversationId(), "Visualize that daily"));

        assertThat(first.provider()).isEqualTo("mock");
        assertThat(first.metrics()).extracting(AiModels.Metric::key).contains("revenue", "orders");
        assertThat(first.metrics().stream().filter(metric -> metric.key().equals("revenue")).findFirst().orElseThrow().value())
                .isEqualByComparingTo("20.0000");
        assertThat(followUp.charts()).extracting(AiModels.Chart::id).contains("revenue-trend");
        assertThat(aiConversationService.get(tenant, first.conversationId()).messages()).hasSize(4);
        assertThatThrownBy(() -> aiConversationService.get(otherTenant, first.conversationId()))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).status().value())
                .isEqualTo(404);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_tool_executions WHERE conversation_id = ?", Long.class,
                first.conversationId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_tool_executions WHERE input_metadata LIKE ?", Long.class,
                "%" + tenant.storeId() + "%")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_request_telemetry WHERE conversation_id = ? AND store_id = ? "
                        + "AND user_id = ? AND status = 'SUCCEEDED' AND provider = 'mock' "
                        + "AND model = 'deterministic-v1' AND total_tokens IS NULL",
                Long.class, first.conversationId(), tenant.storeId(), tenant.userId())).isEqualTo(2);
    }

    private StoreContext tenant(String email, String storeName) {
        var principal = authenticationService.register(new AuthModels.RegisterRequest(
                email,
                "a-secure-password",
                storeName + " Owner",
                storeName,
                "USD",
                "America/New_York"));
        return new StoreContext(principal.userId(), principal.storeId(), principal.role());
    }

    private ProductModels.Response product(StoreContext tenant, String sku, long quantity) {
        return productService.create(tenant, new ProductModels.CreateRequest(
                sku,
                sku + " Product",
                "General",
                new BigDecimal("10.0000"),
                new BigDecimal("4.0000"),
                quantity,
                1L));
    }
}
