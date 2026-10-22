package com.stockflow.analysis;

import com.stockflow.analytics.AnalyticsModels;
import com.stockflow.analytics.AnalyticsPeriodResolver;
import com.stockflow.analytics.AnalyticsService;
import com.stockflow.analysis.BusinessAnalysisModels.AnalysisBundle;
import com.stockflow.analysis.BusinessAnalysisModels.CreateReportRequest;
import com.stockflow.analysis.BusinessAnalysisModels.DimensionRevenue;
import com.stockflow.analysis.BusinessAnalysisModels.InventoryDiagnostic;
import com.stockflow.analysis.BusinessAnalysisModels.RevenueChangeAnalysis;
import com.stockflow.analysis.BusinessAnalysisModels.SalesSnapshot;
import com.stockflow.analysis.BusinessAnalysisModels.ScenarioRequest;
import com.stockflow.analysis.BusinessAnalysisModels.ScenarioResult;
import com.stockflow.analysis.BusinessAnalysisModels.ScenarioType;
import com.stockflow.common.error.ApiExceptions;
import com.stockflow.store.Store;
import com.stockflow.store.StoreContext;
import com.stockflow.store.StoreRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessAnalysisEngine {

    private final AnalyticsService analytics;
    private final AnalyticsPeriodResolver periods;
    private final BusinessAnalysisQueryRepository queries;
    private final StoreRepository stores;
    private final MeterRegistry meters;

    BusinessAnalysisEngine(
            AnalyticsService analytics,
            AnalyticsPeriodResolver periods,
            BusinessAnalysisQueryRepository queries,
            StoreRepository stores,
            MeterRegistry meters) {
        this.analytics = analytics;
        this.periods = periods;
        this.queries = queries;
        this.stores = stores;
        this.meters = meters;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AnalysisBundle analyze(StoreContext context, CreateReportRequest request) {
        var sample = Timer.start(meters);
        try {
            Store store = stores.findById(context.storeId()).orElseThrow(() -> ApiExceptions.notFound("Store"));
            var currentPeriod = periods.resolve(
                    request.preset(), request.startDate(), request.endDateExclusive(), store.getTimeZone());
            var previousPeriod = periods.previous(currentPeriod);
            var currentOverview = analytics.getRevenueSummary(
                    context, AnalyticsModels.PeriodPreset.CUSTOM,
                    currentPeriod.startDate(), currentPeriod.endDateExclusive(), false);
            var previousOverview = analytics.getRevenueSummary(
                    context, AnalyticsModels.PeriodPreset.CUSTOM,
                    previousPeriod.startDate(), previousPeriod.endDateExclusive(), false);
            var productFacts = queries.productFacts(context.storeId(), currentPeriod, previousPeriod);
            var currentFinancials = queries.financialTotals(context.storeId(), currentPeriod);
            var previousFinancials = queries.financialTotals(context.storeId(), previousPeriod);

            var current = new SalesSnapshot(
                    currentOverview.metrics().revenue(), currentOverview.metrics().orders(),
                    currentOverview.metrics().unitsSold(), currentOverview.metrics().averageOrderValue(),
                    BusinessAnalysisCalculations.money(currentFinancials.grossProfit()));
            var previous = new SalesSnapshot(
                    previousOverview.metrics().revenue(), previousOverview.metrics().orders(),
                    previousOverview.metrics().unitsSold(), previousOverview.metrics().averageOrderValue(),
                    BusinessAnalysisCalculations.money(previousFinancials.grossProfit()));
            var revenueChange = BusinessAnalysisCalculations.change(previous.revenue(), current.revenue());
            var productDimensions = productFacts.stream().map(item -> new DimensionRevenue(
                    item.productId().toString(), item.name(), item.previousRevenue(), item.currentRevenue())).toList();
            var productContributions = BusinessAnalysisCalculations.contributions(
                    productDimensions, revenueChange.absoluteChange());
            var categoryContributions = BusinessAnalysisCalculations.contributions(
                    queries.categoryRevenue(context.storeId(), currentPeriod, previousPeriod),
                    revenueChange.absoluteChange());

            var revenue = new RevenueChangeAnalysis(
                    currentPeriod.response(), previousPeriod.response(), store.getCurrencyCode(), previous, current,
                    revenueChange,
                    BusinessAnalysisCalculations.change(
                            BigDecimal.valueOf(previous.orders()), BigDecimal.valueOf(current.orders())),
                    BusinessAnalysisCalculations.change(
                            BigDecimal.valueOf(previous.unitsSold()), BigDecimal.valueOf(current.unitsSold())),
                    BusinessAnalysisCalculations.change(previous.averageOrderValue(), current.averageOrderValue()),
                    productContributions, categoryContributions,
                    BusinessAnalysisCalculations.reconciliationResidual(
                            revenueChange.absoluteChange(), productContributions),
                    BusinessAnalysisCalculations.reconciliationResidual(
                            revenueChange.absoluteChange(), categoryContributions));

            var inventory = inventoryDiagnostics(
                    queries.inventoryFacts(context.storeId(), currentPeriod, previousPeriod), currentPeriod.dayCount());
            var scenarios = scenarios(request.scenarios(), store.getCurrencyCode(), currentFinancials, inventory);
            var limitations = new ArrayList<String>();
            if (productFacts.isEmpty()) {
                limitations.add("Insufficient paid-sales evidence exists in the current and comparison periods for product-level diagnosis.");
            }
            limitations.add("Historical stockout duration is unavailable because inventory snapshots are not recorded.");
            limitations.add("Inventory turnover is unavailable because average historical inventory value is not recorded.");
            limitations.add("Returns are not modeled because the current order lifecycle has no return/refund state.");
            limitations.addAll(BusinessAnalysisCalculations.priceVolume(productFacts).limitations());

            return new AnalysisBundle(
                    revenue, BusinessAnalysisCalculations.priceVolume(productFacts), inventory,
                    currentFinancials, scenarios, limitations);
        } finally {
            sample.stop(meters.timer("stockflow.business_analysis.analytics.duration"));
        }
    }

    private List<InventoryDiagnostic> inventoryDiagnostics(
            List<BusinessAnalysisQueryRepository.InventoryPeriodRow> rows, long dayCount) {
        return rows.stream().map(row -> {
            var velocity = BigDecimal.valueOf(row.currentUnits())
                    .divide(BigDecimal.valueOf(dayCount), 4, RoundingMode.HALF_UP);
            var coverage = velocity.compareTo(BigDecimal.ZERO) == 0 ? null
                    : BigDecimal.valueOf(row.availableQuantity()).divide(velocity, 2, RoundingMode.HALF_UP);
            String risk = row.availableQuantity() == 0 ? "OUT_OF_STOCK"
                    : row.availableQuantity() <= row.reorderPoint() ? "LOW_STOCK"
                    : coverage != null && coverage.compareTo(new BigDecimal("14")) <= 0 ? "AT_RISK"
                    : row.currentUnits() == 0 ? "NO_SALES" : "HEALTHY";
            boolean excess = row.availableQuantity() > row.reorderPoint()
                    && (row.currentUnits() == 0 || coverage != null && coverage.compareTo(new BigDecimal("90")) > 0);
            var salesChange = BusinessAnalysisCalculations.change(
                    BigDecimal.valueOf(row.previousUnits()), BigDecimal.valueOf(row.currentUnits()));
            return new InventoryDiagnostic(
                    row.productId(), row.sku(), row.name(), row.category(), row.availableQuantity(),
                    row.reservedQuantity(), row.reorderPoint(), row.previousUnits(), row.currentUnits(),
                    salesChange.percentageChange(), velocity, coverage, risk, excess, false);
        }).toList();
    }

    private List<ScenarioResult> scenarios(
            List<ScenarioRequest> requests,
            String currency,
            BusinessAnalysisModels.FinancialTotals financials,
            List<InventoryDiagnostic> inventory) {
        if (requests.isEmpty()) return List.of();
        BigDecimal aggregateCoverage = inventory.stream()
                .filter(item -> item.daysOfCover() != null)
                .map(InventoryDiagnostic::daysOfCover)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long coverageCount = inventory.stream().filter(item -> item.daysOfCover() != null).count();
        BigDecimal averageCoverage = coverageCount == 0 ? null
                : aggregateCoverage.divide(BigDecimal.valueOf(coverageCount), 4, RoundingMode.HALF_UP);
        return requests.stream().map(request -> {
            if (request.type() == null || request.changePercent() == null) {
                throw ApiExceptions.badRequest(
                        "INVALID_SCENARIO", "Each scenario requires a type and changePercent");
            }
            return BusinessAnalysisCalculations.scenario(
                    request.type(), request.changePercent(), currency, financials.revenue(),
                    financials.grossProfit(), financials.discounts(),
                    request.type() == ScenarioType.SALES_VELOCITY_CHANGE ? averageCoverage : null);
        }).toList();
    }
}
