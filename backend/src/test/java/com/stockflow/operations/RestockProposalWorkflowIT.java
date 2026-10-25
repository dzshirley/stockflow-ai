package com.stockflow.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockflow.analysis.BusinessAnalysisModels;
import com.stockflow.analysis.BusinessReportService;
import com.stockflow.auth.AuthModels;
import com.stockflow.auth.AuthenticationService;
import com.stockflow.common.error.ApiException;
import com.stockflow.inventory.InventoryModels;
import com.stockflow.inventory.InventoryService;
import com.stockflow.product.ProductModels;
import com.stockflow.product.ProductService;
import com.stockflow.store.StoreContext;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
class RestockProposalWorkflowIT {
    private static final Logger LOG = LoggerFactory.getLogger(RestockProposalWorkflowIT.class);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("stockflow_restock_test").withUsername("stockflow").withPassword("stockflow_test")
            .withStartupTimeout(Duration.ofSeconds(30))
            .withCommand("postgres", "-c", "lock_timeout=5s", "-c", "statement_timeout=30s",
                    "-c", "deadlock_timeout=1s", "-c", "log_lock_waits=on");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("stockflow.scheduling.enabled", () -> "false");
    }

    @Autowired AuthenticationService authentication;
    @Autowired ProductService products;
    @Autowired InventoryService inventory;
    @Autowired BusinessReportService reports;
    @Autowired RestockProposalService proposals;
    @Autowired RestockProposalPersistenceService persistence;
    @Autowired RestockExecutionRecoveryService recovery;
    @Autowired JdbcTemplate jdbc;
    private ScheduledExecutorService diagnostics;

    @BeforeEach
    @Timeout(30)
    void clean(TestInfo testInfo) {
        diagnostics = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("restock-test-diagnostics").factory());
        diagnostics.schedule(() -> logWaitDiagnostics(testInfo.getDisplayName()), 10, TimeUnit.SECONDS);
        jdbc.execute("TRUNCATE TABLE app_users, stores CASCADE");
    }

    @AfterEach
    void stopDiagnostics() {
        if (diagnostics != null) diagnostics.shutdownNow();
    }

    @Test
    void requiresApprovalAndReceiptThenExecutesExactlyOnceWithAuditAndOutbox() {
        var admin = tenant("restock-admin@example.com", "Restock Store");
        var source = source(admin, "RISK-ONE");
        var generated = proposals.generate(admin, "generate-one", new RestockProposalModels.GenerateRequest(
                "Analyze our inventory and prepare a restock proposal for products at risk of running out.",
                source.reportId(), source.recommendationId())).items().getFirst();

        assertThat(generated.status()).isEqualTo(RestockProposalStatus.PROPOSED);
        assertThat(inventory.get(admin, generated.productId()).availableQuantity()).isZero();

        var approved = proposals.approve(admin, generated.id(), generated.version());
        assertThat(approved.status()).isEqualTo(RestockProposalStatus.APPROVED);
        assertThat(inventory.get(admin, generated.productId()).availableQuantity()).isZero();
        assertThatThrownBy(() -> proposals.execute(admin, approved.id(), UUID.randomUUID(),
                new RestockProposalModels.ExecuteCommand(false, approved.version())))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo("RESTOCK_RECEIPT_CONFIRMATION_REQUIRED");

        var commandId = UUID.randomUUID();
        var executed = proposals.execute(admin, approved.id(), commandId,
                new RestockProposalModels.ExecuteCommand(true, approved.version()));
        var replayed = proposals.execute(admin, approved.id(), commandId,
                new RestockProposalModels.ExecuteCommand(true, approved.version()));

        assertThat(executed.status()).isEqualTo(RestockProposalStatus.EXECUTED);
        assertThat(replayed.inventoryAdjustmentId()).isEqualTo(executed.inventoryAdjustmentId());
        assertThat(inventory.get(admin, generated.productId()).availableQuantity())
                .isEqualTo(generated.proposedQuantity());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM inventory_adjustments WHERE operation_command_id = ?", Long.class, commandId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'RESTOCK_RECEIPT_RECORDED'",
                Long.class, generated.id())).isEqualTo(1);
        assertThat(proposals.history(admin, generated.id()).events())
                .extracting(RestockProposalModels.EventResponse::toStatus)
                .containsExactly(RestockProposalStatus.PROPOSED, RestockProposalStatus.APPROVED,
                        RestockProposalStatus.EXECUTING, RestockProposalStatus.EXECUTED);
    }

    @Test
    void enforcesRolesOwnershipCrossStoreAndLegalTransitions() {
        var admin = tenant("role-admin@example.com", "Role Restock");
        var staffPrincipal = authentication.createStaff(admin.storeId(), new AuthModels.RegisterRequest(
                "role-staff@example.com", "a-secure-password", "Staff", "unused", "USD", "America/New_York"));
        var staff = new StoreContext(staffPrincipal.userId(), staffPrincipal.storeId(), staffPrincipal.role());
        var other = tenant("role-other@example.com", "Other Restock");
        var source = source(staff, "RISK-STAFF");
        var proposal = proposals.generate(staff, "staff-generate", new RestockProposalModels.GenerateRequest(
                "Prepare a restock proposal", source.reportId(), source.recommendationId())).items().getFirst();

        assertThat(proposals.get(staff, proposal.id()).id()).isEqualTo(proposal.id());
        assertThat(proposals.get(admin, proposal.id()).id()).isEqualTo(proposal.id());
        assertThatThrownBy(() -> proposals.get(other, proposal.id())).isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status().value()).isEqualTo(404);
        assertThatThrownBy(() -> proposals.approve(staff, proposal.id(), proposal.version()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).status().value()).isEqualTo(403);

        var approved = proposals.approve(admin, proposal.id(), proposal.version());
        assertThatThrownBy(() -> proposals.approve(admin, proposal.id(), approved.version()))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code()).isEqualTo("RESTOCK_STATE_CONFLICT");
    }

    @RepeatedTest(3)
    void changedInventoryMarksApprovedProposalStaleWithoutAddingStock() {
        var admin = tenant("stale-admin@example.com", "Stale Restock");
        var source = source(admin, "RISK-STALE");
        var proposal = proposals.generate(admin, "stale-generate", new RestockProposalModels.GenerateRequest(
                "Prepare a restock proposal", source.reportId(), source.recommendationId())).items().getFirst();
        var approved = proposals.approve(admin, proposal.id(), proposal.version());
        inventory.adjust(admin, proposal.productId(), new InventoryModels.AdjustmentRequest(1L, null, "COUNT_CORRECTION"));

        assertThatThrownBy(() -> proposals.execute(admin, approved.id(), UUID.randomUUID(),
                new RestockProposalModels.ExecuteCommand(true, approved.version())))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code()).isEqualTo("INVENTORY_CHANGED");
        assertThat(proposals.get(admin, proposal.id()).status()).isEqualTo(RestockProposalStatus.STALE);
        assertThat(inventory.get(admin, proposal.productId()).availableQuantity()).isEqualTo(1);
    }

    @Test
    void concurrentReceiptCommandsIncrementInventoryOnlyOnce() throws Exception {
        var admin = tenant("race-admin@example.com", "Race Restock");
        var source = source(admin, "RISK-RACE");
        var proposal = proposals.generate(admin, "race-generate", new RestockProposalModels.GenerateRequest(
                "Prepare a restock proposal", source.reportId(), source.recommendationId())).items().getFirst();
        var approved = proposals.approve(admin, proposal.id(), proposal.version());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> executeSafely(admin, approved));
            var second = executor.submit(() -> executeSafely(admin, approved));
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("EXECUTED", "CONFLICT");
        }
        assertThat(inventory.get(admin, proposal.productId()).availableQuantity())
                .isEqualTo(proposal.proposedQuantity());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM inventory_adjustments WHERE reason = 'RESTOCK_RECEIPT'", Long.class)).isEqualTo(1);
    }

    @Test
    void outboxFailureRollsBackReceiptAndAllowsAnExplicitRetry() {
        var admin = tenant("rollback-admin@example.com", "Rollback Restock");
        var source = source(admin, "RISK-ROLLBACK");
        var proposal = proposals.generate(admin, "rollback-generate", new RestockProposalModels.GenerateRequest(
                "Prepare a restock proposal", source.reportId(), source.recommendationId())).items().getFirst();
        var approved = proposals.approve(admin, proposal.id(), proposal.version());
        var failedCommand = UUID.randomUUID();

        jdbc.execute("""
                CREATE OR REPLACE FUNCTION reject_restock_outbox() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = 'RESTOCK_RECEIPT_RECORDED' THEN
                        RAISE EXCEPTION 'injected outbox failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_restock_outbox_insert
                BEFORE INSERT ON outbox_events
                FOR EACH ROW EXECUTE FUNCTION reject_restock_outbox()
                """);
        try {
            assertThatThrownBy(() -> proposals.execute(admin, approved.id(), failedCommand,
                    new RestockProposalModels.ExecuteCommand(true, approved.version())))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_restock_outbox_insert ON outbox_events");
            jdbc.execute("DROP FUNCTION IF EXISTS reject_restock_outbox()");
        }

        var failed = proposals.get(admin, proposal.id());
        assertThat(failed.status()).isEqualTo(RestockProposalStatus.FAILED);
        assertThat(inventory.get(admin, proposal.productId()).availableQuantity()).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM inventory_adjustments WHERE operation_command_id = ?",
                Long.class, failedCommand)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'RESTOCK_RECEIPT_RECORDED'",
                Long.class, proposal.id())).isZero();

        var executed = proposals.execute(admin, failed.id(), UUID.randomUUID(),
                new RestockProposalModels.ExecuteCommand(true, failed.version()));
        assertThat(executed.status()).isEqualTo(RestockProposalStatus.EXECUTED);
        assertThat(inventory.get(admin, proposal.productId()).availableQuantity())
                .isEqualTo(proposal.proposedQuantity());
        assertThat(proposals.history(admin, proposal.id()).attempts())
                .extracting(RestockProposalModels.AttemptResponse::status)
                .containsExactly("SUCCEEDED", "FAILED");
    }

    @Test
    void generationReplaysByKeyAndRejectsChangedPayload() {
        var admin = tenant("replay-admin@example.com", "Replay Restock");
        var source = source(admin, "RISK-REPLAY");
        var request = new RestockProposalModels.GenerateRequest(
                "Prepare a restock proposal", source.reportId(), source.recommendationId());
        var created = proposals.generate(admin, "same-generation", request);
        var replayed = proposals.generate(admin, "same-generation", request);
        assertThat(replayed.items()).extracting(RestockProposalModels.ProposalResponse::id)
                .containsExactlyElementsOf(created.items().stream().map(RestockProposalModels.ProposalResponse::id).toList());
        assertThatThrownBy(() -> proposals.generate(admin, "same-generation",
                new RestockProposalModels.GenerateRequest(
                        "Prepare a different proposal", source.reportId(), source.recommendationId())))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code()).isEqualTo("RESTOCK_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void expiredExecutionLeaseReturnsToApprovedOnlyWhenNoReceiptCommitted() {
        var admin = tenant("recovery-admin@example.com", "Recovery Restock");
        var source = source(admin, "RISK-RECOVERY");
        var proposal = proposals.generate(admin, "recovery-generate", new RestockProposalModels.GenerateRequest(
                "Prepare a restock proposal", source.reportId(), source.recommendationId())).items().getFirst();
        var approved = proposals.approve(admin, proposal.id(), proposal.version());
        var commandId = UUID.randomUUID();
        persistence.claim(admin, approved.id(), commandId, "0".repeat(64), approved.version());
        jdbc.update("""
                UPDATE restock_proposals
                SET execution_started_at = CURRENT_TIMESTAMP - INTERVAL '3 minutes',
                    execution_lease_until = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE id = ?
                """, proposal.id());

        assertThat(recovery.reconcileExpiredExecutions()).isEqualTo(1);
        assertThat(recovery.reconcileExpiredExecutions()).isZero();
        assertThat(proposals.get(admin, proposal.id()).status()).isEqualTo(RestockProposalStatus.APPROVED);
        assertThat(proposals.history(admin, proposal.id()).attempts()).singleElement()
                .satisfies(attempt -> {
                    assertThat(attempt.status()).isEqualTo("ABANDONED");
                    assertThat(attempt.errorCode()).isEqualTo("EXECUTION_LEASE_EXPIRED");
                });
        assertThat(inventory.get(admin, proposal.productId()).availableQuantity()).isZero();
    }

    private String executeSafely(StoreContext context, RestockProposalModels.ProposalResponse proposal) {
        try {
            return proposals.execute(context, proposal.id(), UUID.randomUUID(),
                    new RestockProposalModels.ExecuteCommand(true, proposal.version())).status().name();
        } catch (ApiException exception) { return "CONFLICT"; }
    }

    private Source source(StoreContext context, String sku) {
        products.create(context, new ProductModels.CreateRequest(
                sku, sku + " Product", "General", new BigDecimal("10.0000"),
                new BigDecimal("4.0000"), 0L, 5L));
        var report = reports.create(context, "report-" + sku, new BusinessAnalysisModels.CreateReportRequest(
                "Analyze inventory risk", BusinessAnalysisModels.AnalysisType.INVENTORY_RISK,
                com.stockflow.analytics.AnalyticsModels.PeriodPreset.LAST_30_DAYS, null, null, List.of()));
        var recommendation = report.report().recommendations().stream()
                .filter(item -> item.title().contains("inventory risk")).findFirst().orElseThrow();
        return new Source(report.report().reportId(), recommendation.id());
    }

    private StoreContext tenant(String email, String name) {
        var user = authentication.register(new AuthModels.RegisterRequest(
                email, "a-secure-password", name + " Owner", name, "USD", "America/New_York"));
        return new StoreContext(user.userId(), user.storeId(), user.role());
    }

    private void logWaitDiagnostics(String testName) {
        LOG.warn("restock_test_slow test={} thresholdSeconds=10", testName);
        String url = POSTGRES.getJdbcUrl();
        url += url.contains("?") ? "&connectTimeout=3&socketTimeout=3" : "?connectTimeout=3&socketTimeout=3";
        try (var connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                        SELECT pid, state, COALESCE(wait_event_type, ''), COALESCE(wait_event, ''),
                               COALESCE(pg_blocking_pids(pid)::text, '{}'),
                               LEFT(REGEXP_REPLACE(query, '\\s+', ' ', 'g'), 240)
                        FROM pg_stat_activity
                        WHERE datname = current_database() AND pid <> pg_backend_pid()
                        ORDER BY pid
                        """)) {
            while (rows.next()) {
                LOG.warn("restock_test_db_activity test={} pid={} state={} waitType={} waitEvent={} blockers={} query={}",
                        testName, rows.getInt(1), rows.getString(2), rows.getString(3), rows.getString(4),
                        rows.getString(5), rows.getString(6));
            }
        } catch (Exception exception) {
            LOG.warn("restock_test_db_diagnostics_failed test={} error={}",
                    testName, exception.getClass().getSimpleName());
        }
        for (var thread : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
            if (thread.getThreadName().equals("main") || thread.getThreadName().startsWith("pool-")) {
                LOG.warn("restock_test_thread test={} name={} state={} lock={} owner={} stack={}",
                        testName, thread.getThreadName(), thread.getThreadState(), thread.getLockName(),
                        thread.getLockOwnerName(), java.util.Arrays.stream(thread.getStackTrace())
                                .limit(12).map(StackTraceElement::toString).toList());
            }
        }
    }

    private record Source(UUID reportId, UUID recommendationId) {}
}
