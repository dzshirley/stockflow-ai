package com.stockflow.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayMigrationTest {

    private static final String DATABASE_URL =
            "jdbc:h2:mem:migration_" + UUID.randomUUID()
                    + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    @Test
    void createsIdentityAndStoreFoundation() throws Exception {
        var migrationResult = Flyway.configure()
                .dataSource(DATABASE_URL, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(migrationResult.migrationsExecuted).isEqualTo(6);

        try (var connection = DriverManager.getConnection(DATABASE_URL, "sa", "");
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE UPPER(table_name) IN "
                                + "('APP_USERS', 'STORES', 'STORE_MEMBERSHIPS', 'PRODUCTS', 'INVENTORY', "
                                + "'INVENTORY_ADJUSTMENTS', 'ORDERS', 'ORDER_ITEMS', "
                                + "'ORDER_IDEMPOTENCY_KEYS', 'OUTBOX_EVENTS', 'AI_CONVERSATIONS', "
                                + "'AI_MESSAGES', 'AI_TOOL_EXECUTIONS', 'AI_REQUEST_TELEMETRY', "
                                + "'BUSINESS_ANALYSIS_REPORTS')")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(15);
        }
    }
}
