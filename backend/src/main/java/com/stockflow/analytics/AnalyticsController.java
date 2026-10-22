package com.stockflow.analytics;

import com.stockflow.analytics.AnalyticsModels.PeriodPreset;
import com.stockflow.analytics.AnalyticsModels.TrendInterval;
import com.stockflow.store.StoreContextProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final StoreContextProvider contextProvider;

    public AnalyticsController(AnalyticsService analyticsService, StoreContextProvider contextProvider) {
        this.analyticsService = analyticsService;
        this.contextProvider = contextProvider;
    }

    @GetMapping("/overview")
    AnalyticsModels.OverviewResponse overview(
            @RequestParam(defaultValue = "LAST_30_DAYS") PeriodPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDateExclusive,
            @RequestParam(defaultValue = "true") boolean comparePreviousPeriod) {
        return analyticsService.getRevenueSummary(
                contextProvider.current(), preset, startDate, endDateExclusive, comparePreviousPeriod);
    }

    @GetMapping("/orders/summary")
    AnalyticsModels.OverviewResponse orderSummary(
            @RequestParam(defaultValue = "LAST_30_DAYS") PeriodPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDateExclusive,
            @RequestParam(defaultValue = "true") boolean comparePreviousPeriod) {
        return analyticsService.getRevenueSummary(
                contextProvider.current(), preset, startDate, endDateExclusive, comparePreviousPeriod);
    }

    @GetMapping("/revenue/trend")
    AnalyticsModels.RevenueTrendResponse revenueTrend(
            @RequestParam(defaultValue = "LAST_30_DAYS") PeriodPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDateExclusive,
            @RequestParam(required = false) TrendInterval interval) {
        return analyticsService.getRevenueTrend(
                contextProvider.current(), preset, startDate, endDateExclusive, interval);
    }

    @GetMapping("/products/top")
    AnalyticsModels.ProductPerformanceResponse topProducts(
            @RequestParam(defaultValue = "LAST_30_DAYS") PeriodPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDateExclusive,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return analyticsService.getTopProducts(
                contextProvider.current(), preset, startDate, endDateExclusive, limit);
    }

    @GetMapping("/categories/performance")
    AnalyticsModels.CategoryPerformanceResponse categoryPerformance(
            @RequestParam(defaultValue = "LAST_30_DAYS") PeriodPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDateExclusive) {
        return analyticsService.getSalesByCategory(
                contextProvider.current(), preset, startDate, endDateExclusive);
    }

    @GetMapping("/inventory/summary")
    AnalyticsModels.InventorySummaryResponse inventorySummary(
            @RequestParam(defaultValue = "LAST_30_DAYS") PeriodPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDateExclusive) {
        return analyticsService.getInventorySummary(
                contextProvider.current(), preset, startDate, endDateExclusive);
    }

    @GetMapping("/inventory/risk")
    AnalyticsModels.InventoryRiskResponse inventoryRisk(
            @RequestParam(defaultValue = "LAST_30_DAYS") PeriodPreset preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDateExclusive,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return analyticsService.getInventoryRisk(
                contextProvider.current(), preset, startDate, endDateExclusive, limit);
    }
}
