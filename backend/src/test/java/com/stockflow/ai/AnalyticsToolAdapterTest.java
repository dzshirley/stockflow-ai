package com.stockflow.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.analytics.AnalyticsModels;
import com.stockflow.analytics.AnalyticsService;
import com.stockflow.common.error.ApiException;
import com.stockflow.store.StoreContext;
import com.stockflow.store.StoreRole;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalyticsToolAdapterTest {

    private AnalyticsService analytics;
    private AiExecutionCollector collector;
    private AnalyticsToolAdapter adapter;
    private AiModels.InvocationContext invocation;

    @BeforeEach
    void setUp() {
        analytics = mock(AnalyticsService.class);
        collector = new AiExecutionCollector();
        adapter = new AnalyticsToolAdapter(
                analytics, collector, mock(AiToolExecutionLogger.class), new ObjectMapper().findAndRegisterModules());
        var storeContext = new StoreContext(UUID.randomUUID(), UUID.randomUUID(), StoreRole.ADMIN);
        invocation = new AiModels.InvocationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), storeContext);
        collector.begin(invocation.requestId());
    }

    @Test
    void suppliesTrustedStoreContextAndCollectsVerifiedResult() {
        var period = new AnalyticsModels.Period(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 21), "UTC");
        var result = new AnalyticsModels.ProductPerformanceResponse(period, "USD", List.of());
        when(analytics.getTopProducts(
                eq(invocation.storeContext()), eq(AnalyticsModels.PeriodPreset.THIS_MONTH),
                eq(null), eq(null), eq(5))).thenReturn(result);

        adapter.topProducts(invocation, "THIS_MONTH", null, null, 5);

        verify(analytics).getTopProducts(
                invocation.storeContext(), AnalyticsModels.PeriodPreset.THIS_MONTH, null, null, 5);
        org.assertj.core.api.Assertions.assertThat(collector.finish(invocation.requestId()))
                .singleElement().satisfies(item -> {
                    org.assertj.core.api.Assertions.assertThat(item.toolName())
                            .isEqualTo(AnalyticsToolAdapter.TOP_PRODUCTS);
                    org.assertj.core.api.Assertions.assertThat(item.result()).isSameAs(result);
                });
    }

    @Test
    void rejectsOutOfBoundsLimitBeforeQueryingAnalytics() {
        assertThatThrownBy(() -> adapter.topProducts(invocation, "LAST_30_DAYS", null, null, 51))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("between 1 and 50");
    }
}
