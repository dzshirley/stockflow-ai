package com.stockflow.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockflow.common.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AnalyticsPeriodResolverTest {

    private final AnalyticsPeriodResolver resolver = new AnalyticsPeriodResolver(
            Clock.fixed(Instant.parse("2026-10-21T16:00:00Z"), ZoneOffset.UTC));

    @Test
    void resolvesStoreCalendarBoundariesAndPreviousPeriod() {
        var period = resolver.resolve(
                AnalyticsModels.PeriodPreset.LAST_7_DAYS, null, null, "America/New_York");
        var previous = resolver.previous(period);

        assertThat(period.startDate()).isEqualTo(LocalDate.parse("2026-10-15"));
        assertThat(period.endDateExclusive()).isEqualTo(LocalDate.parse("2026-10-22"));
        assertThat(period.startInstant()).isEqualTo(Instant.parse("2026-10-15T04:00:00Z"));
        assertThat(previous.startDate()).isEqualTo(LocalDate.parse("2026-10-08"));
        assertThat(previous.endDateExclusive()).isEqualTo(period.startDate());
    }

    @Test
    void comparesLastMonthToACompleteCalendarMonth() {
        var period = resolver.resolve(
                AnalyticsModels.PeriodPreset.LAST_MONTH, null, null, "UTC");
        var previous = resolver.previous(period);

        assertThat(period.startDate()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(period.endDateExclusive()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(previous.startDate()).isEqualTo(LocalDate.parse("2026-08-01"));
        assertThat(previous.endDateExclusive()).isEqualTo(LocalDate.parse("2026-09-01"));
    }

    @Test
    void rejectsInvalidAndOversizedCustomRanges() {
        assertThatThrownBy(() -> resolver.resolve(
                        AnalyticsModels.PeriodPreset.CUSTOM,
                        LocalDate.parse("2026-10-02"),
                        LocalDate.parse("2026-10-01"),
                        "UTC"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("startDate");

        assertThatThrownBy(() -> resolver.resolve(
                        AnalyticsModels.PeriodPreset.CUSTOM,
                        LocalDate.parse("2025-01-01"),
                        LocalDate.parse("2026-10-01"),
                        "UTC"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("366 days");
    }
}
