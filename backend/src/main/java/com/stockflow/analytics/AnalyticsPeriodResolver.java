package com.stockflow.analytics;

import com.stockflow.common.error.ApiExceptions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsPeriodResolver {

    static final long MAX_RANGE_DAYS = 366;

    private final Clock clock;

    public AnalyticsPeriodResolver(Clock clock) {
        this.clock = clock;
    }

    public ResolvedPeriod resolve(
            AnalyticsModels.PeriodPreset preset,
            LocalDate customStart,
            LocalDate customEndExclusive,
            String timeZone) {
        var zone = ZoneId.of(timeZone);
        var today = LocalDate.now(clock.withZone(zone));
        var effectivePreset = preset == null ? AnalyticsModels.PeriodPreset.LAST_30_DAYS : preset;
        LocalDate start;
        LocalDate endExclusive;
        switch (effectivePreset) {
            case LAST_7_DAYS -> {
                endExclusive = today.plusDays(1);
                start = endExclusive.minusDays(7);
            }
            case LAST_30_DAYS -> {
                endExclusive = today.plusDays(1);
                start = endExclusive.minusDays(30);
            }
            case LAST_90_DAYS -> {
                endExclusive = today.plusDays(1);
                start = endExclusive.minusDays(90);
            }
            case THIS_MONTH -> {
                start = today.withDayOfMonth(1);
                endExclusive = today.plusDays(1);
            }
            case LAST_MONTH -> {
                endExclusive = today.withDayOfMonth(1);
                start = endExclusive.minusMonths(1);
            }
            case CUSTOM -> {
                if (customStart == null || customEndExclusive == null) {
                    throw ApiExceptions.badRequest(
                            "INVALID_ANALYTICS_PERIOD",
                            "CUSTOM requires startDate and endDateExclusive");
                }
                start = customStart;
                endExclusive = customEndExclusive;
            }
            default -> throw new IllegalStateException("Unsupported analytics preset");
        }
        validate(start, endExclusive, today);
        return period(effectivePreset, start, endExclusive, zone);
    }

    public ResolvedPeriod previous(ResolvedPeriod current) {
        LocalDate start;
        LocalDate endExclusive = current.startDate();
        if (current.preset() == AnalyticsModels.PeriodPreset.LAST_MONTH) {
            start = endExclusive.minusMonths(1);
        } else {
            start = endExclusive.minusDays(current.dayCount());
        }
        return period(current.preset(), start, endExclusive, current.zone());
    }

    private void validate(LocalDate start, LocalDate endExclusive, LocalDate today) {
        if (!start.isBefore(endExclusive)) {
            throw ApiExceptions.badRequest(
                    "INVALID_ANALYTICS_PERIOD", "startDate must be before endDateExclusive");
        }
        long days = ChronoUnit.DAYS.between(start, endExclusive);
        if (days > MAX_RANGE_DAYS) {
            throw ApiExceptions.badRequest(
                    "ANALYTICS_PERIOD_TOO_LARGE", "Analytics date ranges may not exceed 366 days");
        }
        if (endExclusive.isAfter(today.plusDays(2))) {
            throw ApiExceptions.badRequest(
                    "INVALID_ANALYTICS_PERIOD", "endDateExclusive may not be in the future");
        }
    }

    private ResolvedPeriod period(
            AnalyticsModels.PeriodPreset preset,
            LocalDate start,
            LocalDate endExclusive,
            ZoneId zone) {
        return new ResolvedPeriod(
                preset,
                start,
                endExclusive,
                start.atStartOfDay(zone).toInstant(),
                endExclusive.atStartOfDay(zone).toInstant(),
                zone,
                ChronoUnit.DAYS.between(start, endExclusive));
    }

    public record ResolvedPeriod(
            AnalyticsModels.PeriodPreset preset,
            LocalDate startDate,
            LocalDate endDateExclusive,
            Instant startInstant,
            Instant endInstant,
            ZoneId zone,
            long dayCount) {

        public AnalyticsModels.Period response() {
            return new AnalyticsModels.Period(startDate, endDateExclusive, zone.getId());
        }

        public Duration duration() {
            return Duration.between(startInstant, endInstant);
        }
    }
}
