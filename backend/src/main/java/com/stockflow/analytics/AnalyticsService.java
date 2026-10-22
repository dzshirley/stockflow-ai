package com.stockflow.analytics;

import com.stockflow.analytics.AnalyticsModels.CategoryPerformanceResponse;
import com.stockflow.analytics.AnalyticsModels.InventoryRiskLevel;
import com.stockflow.analytics.AnalyticsModels.InventoryRiskResponse;
import com.stockflow.analytics.AnalyticsModels.InventorySummaryResponse;
import com.stockflow.analytics.AnalyticsModels.OverviewResponse;
import com.stockflow.analytics.AnalyticsModels.PeriodPreset;
import com.stockflow.analytics.AnalyticsModels.ProductPerformanceResponse;
import com.stockflow.analytics.AnalyticsModels.RevenueTrendResponse;
import com.stockflow.analytics.AnalyticsModels.TrendInterval;
import com.stockflow.analytics.AnalyticsPeriodResolver.ResolvedPeriod;
import com.stockflow.common.error.ApiExceptions;
import com.stockflow.store.Store;
import com.stockflow.store.StoreContext;
import com.stockflow.store.StoreRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private final AnalyticsQueryRepository repository;
    private final AnalyticsPeriodResolver periodResolver;
    private final StoreRepository storeRepository;
    private final MeterRegistry meterRegistry;

    public AnalyticsService(
            AnalyticsQueryRepository repository,
            AnalyticsPeriodResolver periodResolver,
            StoreRepository storeRepository,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.periodResolver = periodResolver;
        this.storeRepository = storeRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public OverviewResponse getRevenueSummary(
            StoreContext context,
            PeriodPreset preset,
            LocalDate startDate,
            LocalDate endDateExclusive,
            boolean comparePreviousPeriod) {
        return measured("overview", () -> {
            var store = store(context);
            var period = periodResolver.resolve(preset, startDate, endDateExclusive, store.getTimeZone());
            validateCurrency(context, period, store.getCurrencyCode());
            var current = repository.sales(context.storeId(), period);
            var metrics = salesMetrics(current, store.getCurrencyCode());
            AnalyticsModels.Comparison comparison = null;
            if (comparePreviousPeriod) {
                var previousPeriod = periodResolver.previous(period);
                validateCurrency(context, previousPeriod, store.getCurrencyCode());
                var previous = repository.sales(context.storeId(), previousPeriod);
                var change = money(current.revenue().subtract(previous.revenue()));
                var comparable = previous.revenue().compareTo(BigDecimal.ZERO) != 0;
                BigDecimal growth = AnalyticsCalculations.growthPercent(
                        current.revenue(), previous.revenue());
                comparison = new AnalyticsModels.Comparison(
                        previousPeriod.response(), money(previous.revenue()), change, growth, comparable);
            }
            return new OverviewResponse(period.response(), metrics, comparison);
        });
    }

    @Transactional(readOnly = true)
    public RevenueTrendResponse getRevenueTrend(
            StoreContext context,
            PeriodPreset preset,
            LocalDate startDate,
            LocalDate endDateExclusive,
            TrendInterval interval) {
        return measured("revenue-trend", () -> {
            var store = store(context);
            var period = periodResolver.resolve(preset, startDate, endDateExclusive, store.getTimeZone());
            validateCurrency(context, period, store.getCurrencyCode());
            var effectiveInterval = interval == null ? chooseInterval(period) : interval;
            var rows = repository.revenueTrend(context.storeId(), period, effectiveInterval);
            var byDate = new HashMap<LocalDate, AnalyticsQueryRepository.TrendAggregate>();
            rows.forEach(row -> byDate.put(row.bucketStart(), row));
            var points = new ArrayList<AnalyticsModels.TrendPoint>();
            var cursor = bucketStart(period.startDate(), effectiveInterval);
            var lastBucket = bucketStart(period.endDateExclusive().minusDays(1), effectiveInterval);
            while (!cursor.isAfter(lastBucket)) {
                var row = byDate.get(cursor);
                points.add(row == null
                        ? new AnalyticsModels.TrendPoint(cursor, money(BigDecimal.ZERO), 0, 0)
                        : new AnalyticsModels.TrendPoint(
                                cursor, money(row.revenue()), row.orders(), row.unitsSold()));
                cursor = nextBucket(cursor, effectiveInterval);
            }
            return new RevenueTrendResponse(
                    period.response(), effectiveInterval, store.getCurrencyCode(), points);
        });
    }

    @Transactional(readOnly = true)
    public ProductPerformanceResponse getTopProducts(
            StoreContext context,
            PeriodPreset preset,
            LocalDate startDate,
            LocalDate endDateExclusive,
            int limit) {
        return measured("top-products", () -> {
            var store = store(context);
            var period = periodResolver.resolve(preset, startDate, endDateExclusive, store.getTimeZone());
            validateCurrency(context, period, store.getCurrencyCode());
            return new ProductPerformanceResponse(
                    period.response(),
                    store.getCurrencyCode(),
                    repository.topProducts(context.storeId(), period, limit));
        });
    }

    @Transactional(readOnly = true)
    public CategoryPerformanceResponse getSalesByCategory(
            StoreContext context,
            PeriodPreset preset,
            LocalDate startDate,
            LocalDate endDateExclusive) {
        return measured("categories", () -> {
            var store = store(context);
            var period = periodResolver.resolve(preset, startDate, endDateExclusive, store.getTimeZone());
            validateCurrency(context, period, store.getCurrencyCode());
            return new CategoryPerformanceResponse(
                    period.response(),
                    store.getCurrencyCode(),
                    repository.categories(context.storeId(), period));
        });
    }

    @Transactional(readOnly = true)
    public InventorySummaryResponse getInventorySummary(
            StoreContext context,
            PeriodPreset preset,
            LocalDate startDate,
            LocalDate endDateExclusive) {
        return measured("inventory-summary", () -> {
            var store = store(context);
            var period = periodResolver.resolve(preset, startDate, endDateExclusive, store.getTimeZone());
            validateCurrency(context, period, store.getCurrencyCode());
            var aggregate = repository.inventorySummary(context.storeId(), period);
            var velocity = AnalyticsCalculations.dailyVelocity(aggregate.unitsSold(), period.dayCount());
            return new InventorySummaryResponse(
                    aggregate.activeProducts(),
                    aggregate.availableUnits(),
                    aggregate.reservedUnits(),
                    aggregate.lowStockProducts(),
                    aggregate.unitsSold(),
                    velocity,
                    period.response());
        });
    }

    @Transactional(readOnly = true)
    public InventoryRiskResponse getInventoryRisk(
            StoreContext context,
            PeriodPreset preset,
            LocalDate startDate,
            LocalDate endDateExclusive,
            int limit) {
        return measured("inventory-risk", () -> {
            var store = store(context);
            var period = periodResolver.resolve(preset, startDate, endDateExclusive, store.getTimeZone());
            validateCurrency(context, period, store.getCurrencyCode());
            var items = repository.inventoryRisk(context.storeId(), period, limit).stream()
                    .map(row -> inventoryRisk(row, period.dayCount()))
                    .toList();
            return new InventoryRiskResponse(period.response(), items);
        });
    }

    private AnalyticsModels.SalesMetrics salesMetrics(
            AnalyticsQueryRepository.SalesAggregate aggregate, String currency) {
        var average = AnalyticsCalculations.averageOrderValue(aggregate.revenue(), aggregate.orders());
        return new AnalyticsModels.SalesMetrics(
                money(aggregate.revenue()), currency, aggregate.orders(), average, aggregate.unitsSold());
    }

    private AnalyticsModels.InventoryRisk inventoryRisk(
            AnalyticsQueryRepository.InventoryRiskRow row, long dayCount) {
        var velocity = AnalyticsCalculations.dailyVelocity(row.unitsSold(), dayCount);
        var coverage = AnalyticsCalculations.daysOfCover(row.availableQuantity(), velocity);
        InventoryRiskLevel level;
        if (row.availableQuantity() == 0) {
            level = InventoryRiskLevel.OUT_OF_STOCK;
        } else if (row.availableQuantity() <= row.reorderPoint()) {
            level = InventoryRiskLevel.LOW_STOCK;
        } else if (coverage != null && coverage.compareTo(new BigDecimal("14")) <= 0) {
            level = InventoryRiskLevel.AT_RISK;
        } else if (row.unitsSold() == 0) {
            level = InventoryRiskLevel.NO_SALES;
        } else {
            level = InventoryRiskLevel.HEALTHY;
        }
        return new AnalyticsModels.InventoryRisk(
                row.productId(), row.sku(), row.name(), row.category(),
                row.availableQuantity(), row.reservedQuantity(), row.reorderPoint(), row.unitsSold(),
                velocity, coverage, level);
    }

    private TrendInterval chooseInterval(ResolvedPeriod period) {
        if (period.dayCount() <= 31) return TrendInterval.DAY;
        if (period.dayCount() <= 120) return TrendInterval.WEEK;
        return TrendInterval.MONTH;
    }

    private LocalDate bucketStart(LocalDate date, TrendInterval interval) {
        return switch (interval) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private LocalDate nextBucket(LocalDate date, TrendInterval interval) {
        return switch (interval) {
            case DAY -> date.plusDays(1);
            case WEEK -> date.plusWeeks(1);
            case MONTH -> date.plusMonths(1);
        };
    }

    private Store store(StoreContext context) {
        return storeRepository.findById(context.storeId())
                .orElseThrow(() -> ApiExceptions.notFound("Store"));
    }

    private void validateCurrency(StoreContext context, ResolvedPeriod period, String currency) {
        if (repository.currencyMismatchCount(context.storeId(), period, currency) > 0) {
            throw ApiExceptions.conflict(
                    "ANALYTICS_CURRENCY_MISMATCH",
                    "Paid orders in the selected period contain a currency that differs from the store currency");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private <T> T measured(String operation, Supplier<T> supplier) {
        var sample = Timer.start(meterRegistry);
        try {
            return supplier.get();
        } catch (RuntimeException exception) {
            meterRegistry.counter("stockflow.analytics.errors", "operation", operation).increment();
            log.warn("Analytics operation failed operation={} type={}", operation, exception.getClass().getSimpleName());
            throw exception;
        } finally {
            sample.stop(meterRegistry.timer("stockflow.analytics.duration", "operation", operation));
        }
    }
}
