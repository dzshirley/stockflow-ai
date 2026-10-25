package com.stockflow.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockflow.auth.AuthModels;
import com.stockflow.auth.AuthenticationService;
import com.stockflow.common.error.ApiException;
import com.stockflow.order.OrderModels;
import com.stockflow.order.OrderService;
import com.stockflow.product.ProductModels;
import com.stockflow.product.ProductService;
import com.stockflow.store.StoreContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Timeout(30)
class BusinessAnalysisWorkflowIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("stockflow_analysis_test")
            .withUsername("stockflow")
            .withPassword("stockflow_test")
            .withStartupTimeout(Duration.ofSeconds(30))
            .withCommand("postgres", "-c", "lock_timeout=5s", "-c", "statement_timeout=30s",
                    "-c", "deadlock_timeout=1s", "-c", "log_lock_waits=on");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("stockflow.scheduling.enabled", () -> "false");
    }

    @Autowired AuthenticationService authenticationService;
    @Autowired ProductService productService;
    @Autowired OrderService orderService;
    @Autowired BusinessReportService reports;
    @Autowired BusinessReportPersistenceService persistence;
    @Autowired BusinessReportRecoveryService recovery;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE app_users, stores CASCADE");
    }

    @Test
    void createsReconciledVersionedJsonbReportAndReplaysIdempotently() {
        var admin = tenant("analysis-owner@example.com", "Analysis Store");
        seedComparison(admin);
        var request = request("Why did revenue change and which products contributed most?");

        var created = reports.create(admin, "analysis-key", request);
        var replayed = reports.create(admin, "analysis-key", request);

        assertThat(replayed.metadata().id()).isEqualTo(created.metadata().id());
        assertThat(created.metadata().status()).isEqualTo(BusinessAnalysisReport.Status.COMPLETED);
        assertThat(created.report().schemaVersion()).isEqualTo(1);
        assertThat(created.report().reportVersion()).isEqualTo(1);
        assertThat(created.report().modelMetadata().provider()).isEqualTo("mock");
        assertThat(created.report().metrics()).filteredOn(metric -> metric.key().equals("revenue"))
                .singleElement().satisfies(metric -> {
                    assertThat(metric.value()).isEqualByComparingTo("50.0000");
                    assertThat(metric.comparisonValue()).isEqualByComparingTo("20.0000");
                    assertThat(metric.absoluteChange()).isEqualByComparingTo("30.0000");
                });
        assertThat(created.report().charts()).filteredOn(chart -> chart.id().equals("product-contributions"))
                .singleElement().satisfies(chart -> assertThat(chart.points())
                        .extracting(point -> point.value().toPlainString())
                        .containsExactlyInAnyOrder("10.0000", "20.0000"));
        assertThat(created.report().charts()).extracting(BusinessChart::id)
                .contains("price-volume-decomposition", "inventory-days-of-cover");
        assertThat(created.report().metrics()).extracting(BusinessMetric::key)
                .contains("grossProfit", "priceEffect", "volumeEffect", "newProductEffect");
        assertThat(created.report().findings())
                .anyMatch(finding -> finding.title().equals("Revenue change decomposition"))
                .anyMatch(finding -> finding.title().startsWith("Inventory diagnostic:"));
        assertThat(created.report().findings()).isNotEmpty();
        assertThat(created.report().recommendations()).allMatch(Recommendation::approvalRequired);
        assertThat(created.report().limitations())
                .anyMatch(item -> item.description().contains("Historical stockout duration"));
        assertThat(jdbc.queryForObject(
                "SELECT pg_typeof(report_document)::text FROM business_analysis_reports WHERE id = ?",
                String.class, created.metadata().id())).isEqualTo("jsonb");
        assertThat(jdbc.queryForObject(
                "SELECT retention_until > created_at FROM business_analysis_reports WHERE id = ?",
                Boolean.class, created.metadata().id())).isTrue();

        assertThatThrownBy(() -> reports.create(
                admin, "analysis-key", request("Why did inventory change?")))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("REPORT_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void enforcesAdminStoreWideStaffCreatorOnlyAndCrossStoreIsolation() {
        var admin = tenant("report-admin@example.com", "Role Store");
        var staffPrincipal = authenticationService.createStaff(admin.storeId(), new AuthModels.RegisterRequest(
                "report-staff@example.com", "a-secure-password", "Report Staff", "Role Store",
                "USD", "America/New_York"));
        var staff = new StoreContext(
                staffPrincipal.userId(), staffPrincipal.storeId(), staffPrincipal.role());
        var other = tenant("report-other@example.com", "Other Store");

        var adminReport = reports.create(admin, "admin-report", request("Why did revenue change?"));
        var staffReport = reports.create(staff, "staff-report", request("Which products changed sales?"));

        assertThat(reports.get(admin, staffReport.metadata().id()).metadata().id())
                .isEqualTo(staffReport.metadata().id());
        assertThatThrownBy(() -> reports.get(staff, adminReport.metadata().id()))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).status().value())
                .isEqualTo(404);
        assertThatThrownBy(() -> reports.get(other, staffReport.metadata().id()))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).status().value())
                .isEqualTo(404);
        assertThat(reports.list(admin, null, null, 20).items()).hasSize(2);
        assertThat(reports.list(staff, null, null, 20).items())
                .extracting(BusinessAnalysisModels.ReportSummary::id)
                .containsExactly(staffReport.metadata().id());
    }

    @Test
    void persistsTypedScenarioWithoutClaimingForecastAccuracy() {
        var admin = tenant("scenario@example.com", "Scenario Store");
        seedComparison(admin);
        var request = new BusinessAnalysisModels.CreateReportRequest(
                "What happens to revenue if unit sales increase by 10%?",
                BusinessAnalysisModels.AnalysisType.SCENARIO_ANALYSIS,
                com.stockflow.analytics.AnalyticsModels.PeriodPreset.CUSTOM,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), List.of());

        var result = reports.create(admin, "scenario-key", request);

        assertThat(result.report().scenarios()).singleElement().satisfies(scenario -> {
            assertThat(scenario.type()).isEqualTo(BusinessAnalysisModels.ScenarioType.UNIT_SALES_CHANGE);
            assertThat(scenario.baselineValue()).isEqualByComparingTo("50.0000");
            assertThat(scenario.modeledValue()).isEqualByComparingTo("55.0000");
            assertThat(scenario.modeledGrossProfit()).isEqualByComparingTo("33.0000");
            assertThat(scenario.statement()).contains("not a forecast");
        });

        var decrease = reports.create(admin, "scenario-decrease-key", new BusinessAnalysisModels.CreateReportRequest(
                "What if unit sales decrease by 10%?", BusinessAnalysisModels.AnalysisType.AUTO,
                com.stockflow.analytics.AnalyticsModels.PeriodPreset.CUSTOM,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), List.of()));
        assertThat(decrease.report().scenarios()).singleElement()
                .satisfies(scenario -> {
                    assertThat(scenario.changePercent()).isEqualByComparingTo("-10.00");
                    assertThat(scenario.modeledValue()).isEqualByComparingTo("45.0000");
                });
    }

    @Test
    void reconcilesCrashedPendingAndTimedOutRunningReportsWithoutTouchingAnActiveLease() {
        var admin = tenant("report-recovery@example.com", "Recovery Store");
        var pending = begin(admin, "crashed-pending");
        var expiredRunning = begin(admin, "expired-running");
        var activeRunning = begin(admin, "active-running");
        var expiredOwner = UUID.randomUUID();
        persistence.markRunning(expiredRunning, expiredOwner);
        persistence.markRunning(activeRunning, UUID.randomUUID());
        jdbc.update("UPDATE business_analysis_reports SET updated_at = CURRENT_TIMESTAMP - INTERVAL '10 minutes' WHERE id = ?", pending);
        jdbc.update("""
                UPDATE business_analysis_reports
                SET generation_started_at = CURRENT_TIMESTAMP - INTERVAL '3 minutes',
                    generation_lease_until = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE id = ?
                """, expiredRunning);

        assertThat(recovery.reconcileExpiredReports()).isEqualTo(2);

        assertThat(status(pending)).isEqualTo("FAILED");
        assertThat(status(expiredRunning)).isEqualTo("FAILED");
        assertThat(status(activeRunning)).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject(
                "SELECT failure_code FROM business_analysis_reports WHERE id = ?", String.class, pending))
                .isEqualTo("REPORT_GENERATION_EXPIRED");
        var staleCompletionTime = Instant.now();
        assertThatThrownBy(() -> persistence.complete(
                expiredRunning,
                expiredOwner,
                "{}",
                new BusinessAnalysisReport.ModelMetadata(
                        "mock", "deterministic-v1", "business-analysis-v1",
                        null, null, null, null, null, 0),
                staleCompletionTime,
                staleCompletionTime))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("REPORT_GENERATION_STALE");
        assertThat(jdbc.queryForObject(
                "SELECT failure_code FROM business_analysis_reports WHERE id = ?", String.class, expiredRunning))
                .isEqualTo("REPORT_GENERATION_EXPIRED");
    }

    @Test
    void concurrentRecoveryClaimsAnExpiredReportOnlyOnce() throws Exception {
        var admin = tenant("report-recovery-race@example.com", "Recovery Race Store");
        var pending = begin(admin, "concurrent-pending");
        jdbc.update("UPDATE business_analysis_reports SET updated_at = CURRENT_TIMESTAMP - INTERVAL '10 minutes' WHERE id = ?", pending);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(recovery::reconcileExpiredReports);
            var second = executor.submit(recovery::reconcileExpiredReports);
            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }

        assertThat(status(pending)).isEqualTo("FAILED");
    }

    @Test
    void rejectsAValidJsonDocumentWhoseOwnershipMetadataDoesNotMatchTheAuthorizedRow() {
        var admin = tenant("report-integrity@example.com", "Integrity Store");
        var other = tenant("report-integrity-other@example.com", "Other Integrity Store");
        var created = reports.create(admin, "integrity-report", request("Why did revenue change?"));
        jdbc.update("""
                UPDATE business_analysis_reports
                SET report_document = jsonb_set(
                    report_document,
                    '{storeId}',
                    CAST(('"' || CAST(? AS text) || '"') AS jsonb))
                WHERE id = ?
                """, other.storeId(), created.metadata().id());

        assertThatThrownBy(() -> reports.get(admin, created.metadata().id()))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("BUSINESS_REPORT_CORRUPT");
        assertThatThrownBy(() -> reports.get(other, created.metadata().id()))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).status().value())
                .isEqualTo(404);
    }

    private UUID begin(StoreContext context, String key) {
        return persistence.begin(
                context,
                key,
                "0".repeat(64),
                BusinessAnalysisModels.AnalysisType.INVENTORY_RISK,
                "Inventory risk",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 1),
                "America/New_York").reportId();
    }

    private String status(UUID reportId) {
        return jdbc.queryForObject(
                "SELECT status FROM business_analysis_reports WHERE id = ?", String.class, reportId);
    }

    private void seedComparison(StoreContext context) {
        var existing = product(context, "EXISTING", 20);
        var introduced = product(context, "INTRODUCED", 2);
        var previous = orderService.create(context, "previous-order", new OrderModels.CreateRequest(
                List.of(new OrderModels.LineRequest(existing.id(), 2))));
        orderService.payment(context, previous.id(), OrderModels.PaymentOutcome.APPROVED);
        var current = orderService.create(context, "current-order", new OrderModels.CreateRequest(List.of(
                new OrderModels.LineRequest(existing.id(), 3),
                new OrderModels.LineRequest(introduced.id(), 2))));
        orderService.payment(context, current.id(), OrderModels.PaymentOutcome.APPROVED);
        jdbc.update("UPDATE orders SET paid_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-08-15T16:00:00Z")), previous.id());
        jdbc.update("UPDATE orders SET paid_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-09-15T16:00:00Z")), current.id());
    }

    private BusinessAnalysisModels.CreateReportRequest request(String question) {
        return new BusinessAnalysisModels.CreateReportRequest(
                question, BusinessAnalysisModels.AnalysisType.REVENUE_INVESTIGATION,
                com.stockflow.analytics.AnalyticsModels.PeriodPreset.CUSTOM,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), List.of());
    }

    private StoreContext tenant(String email, String storeName) {
        var principal = authenticationService.register(new AuthModels.RegisterRequest(
                email, "a-secure-password", storeName + " Owner", storeName,
                "USD", "America/New_York"));
        return new StoreContext(principal.userId(), principal.storeId(), principal.role());
    }

    private ProductModels.Response product(StoreContext context, String sku, long initialQuantity) {
        return productService.create(context, new ProductModels.CreateRequest(
                sku, sku + " Product", "General", new BigDecimal("10.0000"),
                new BigDecimal("4.0000"), initialQuantity, 1L));
    }
}
