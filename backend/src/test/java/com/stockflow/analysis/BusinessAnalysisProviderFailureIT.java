package com.stockflow.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.stockflow.ai.BusinessAnalysisAiService;
import com.stockflow.auth.AuthModels;
import com.stockflow.auth.AuthenticationService;
import com.stockflow.common.error.ApiException;
import com.stockflow.store.StoreContext;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class BusinessAnalysisProviderFailureIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("stockflow_analysis_failure_test")
            .withUsername("stockflow")
            .withPassword("stockflow_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired AuthenticationService authenticationService;
    @Autowired BusinessReportService reports;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean BusinessAnalysisAiService ai;

    @Test
    void leavesAnExplicitFailedReportWithoutFabricatedDocument() {
        when(ai.synthesize(anyString())).thenThrow(new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_PROVIDER_UNAVAILABLE",
                "The business-analysis provider is temporarily unavailable"));
        var principal = authenticationService.register(new AuthModels.RegisterRequest(
                "report-failure@example.com", "a-secure-password", "Report Owner", "Failure Store",
                "USD", "America/New_York"));
        var context = new StoreContext(principal.userId(), principal.storeId(), principal.role());
        var request = new BusinessAnalysisModels.CreateReportRequest(
                "Why did revenue change?", BusinessAnalysisModels.AnalysisType.REVENUE_INVESTIGATION,
                com.stockflow.analytics.AnalyticsModels.PeriodPreset.CUSTOM,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), List.of());

        assertThatThrownBy(() -> reports.create(context, "failed-report", request))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo("AI_PROVIDER_UNAVAILABLE");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM business_analysis_reports WHERE store_id = ? AND status = 'FAILED' "
                        + "AND failure_code = 'AI_PROVIDER_UNAVAILABLE' AND report_document IS NULL",
                Long.class, context.storeId())).isEqualTo(1);
    }
}
