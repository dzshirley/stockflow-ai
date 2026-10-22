package com.stockflow.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockflow.analysis.BusinessAnalysisModels.DimensionRevenue;
import com.stockflow.analysis.BusinessAnalysisModels.ProductLifecycle;
import com.stockflow.analysis.BusinessAnalysisModels.ProductPeriodFacts;
import com.stockflow.analysis.BusinessAnalysisModels.ScenarioType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessAnalysisCalculationsTest {

    @Test
    void calculatesPositiveNegativeAndZeroChangesWithoutInventingZeroBaselines() {
        assertThat(BusinessAnalysisCalculations.change(new BigDecimal("100"), new BigDecimal("120")))
                .satisfies(change -> {
                    assertThat(change.absoluteChange()).isEqualByComparingTo("20.0000");
                    assertThat(change.percentageChange()).isEqualByComparingTo("20.0000");
                    assertThat(change.comparable()).isTrue();
                });
        assertThat(BusinessAnalysisCalculations.change(new BigDecimal("100"), new BigDecimal("75")).percentageChange())
                .isEqualByComparingTo("-25.0000");
        assertThat(BusinessAnalysisCalculations.change(BigDecimal.ZERO, new BigDecimal("10")))
                .satisfies(change -> {
                    assertThat(change.comparable()).isFalse();
                    assertThat(change.percentageChange()).isNull();
                });
    }

    @Test
    void reconcilesProductAndCategoryContributionsIncludingOnePeriodDimensions() {
        var contributions = BusinessAnalysisCalculations.contributions(List.of(
                new DimensionRevenue("existing", "Existing", new BigDecimal("100"), new BigDecimal("80")),
                new DimensionRevenue("new", "New", BigDecimal.ZERO, new BigDecimal("30")),
                new DimensionRevenue("gone", "Gone", new BigDecimal("15"), BigDecimal.ZERO)),
                new BigDecimal("-5"));

        assertThat(contributions).extracting(item -> item.contribution().toPlainString())
                .containsExactly("30.0000", "-20.0000", "-15.0000");
        assertThat(BusinessAnalysisCalculations.reconciliationResidual(new BigDecimal("-5"), contributions))
                .isEqualByComparingTo("0.0000");
    }

    @Test
    void symmetricPriceVolumeAndLifecycleEffectsReconcileExactly() {
        var continuing = facts("continuing", 10, 12, "100", "144");
        var introduced = facts("new", 0, 3, "0", "30");
        var discontinued = facts("gone", 4, 0, "20", "0");

        var result = BusinessAnalysisCalculations.priceVolume(List.of(continuing, introduced, discontinued));

        assertThat(result.totalRevenueChange()).isEqualByComparingTo("54.0000");
        assertThat(result.priceEffect()).isEqualByComparingTo("22.0000");
        assertThat(result.volumeEffect()).isEqualByComparingTo("22.0000");
        assertThat(result.newProductEffect()).isEqualByComparingTo("30.0000");
        assertThat(result.discontinuedProductEffect()).isEqualByComparingTo("-20.0000");
        assertThat(result.residual()).isEqualByComparingTo("0.0000");
        assertThat(result.complete()).isTrue();
        assertThat(result.items()).extracting(BusinessAnalysisModels.PriceVolumeItem::lifecycle)
                .containsExactly(ProductLifecycle.CONTINUING, ProductLifecycle.NEW, ProductLifecycle.DISCONTINUED);
    }

    @Test
    void unitSalesAndPriceScenariosExposeAssumptionsAndProfitArithmetic() {
        var unitSales = BusinessAnalysisCalculations.scenario(
                ScenarioType.UNIT_SALES_CHANGE, new BigDecimal("10"), "USD",
                new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("5"), null);
        assertThat(unitSales.modeledRevenue()).isEqualByComparingTo("110.0000");
        assertThat(unitSales.modeledGrossProfit()).isEqualByComparingTo("44.0000");

        var price = BusinessAnalysisCalculations.scenario(
                ScenarioType.PRICE_CHANGE, new BigDecimal("5"), "USD",
                new BigDecimal("100"), new BigDecimal("40"), BigDecimal.ZERO, null);
        assertThat(price.modeledRevenue()).isEqualByComparingTo("105.0000");
        assertThat(price.modeledGrossProfit()).isEqualByComparingTo("45.0000");
        assertThat(price.limitations()).anyMatch(item -> item.contains("elasticity"));
    }

    @Test
    void discountAndCoverageScenariosHandleUnavailableAndBoundaryValues() {
        var discount = BusinessAnalysisCalculations.scenario(
                ScenarioType.DISCOUNT_CHANGE, new BigDecimal("10"), "USD",
                new BigDecimal("100"), null, new BigDecimal("20"), null);
        assertThat(discount.modeledRevenue()).isEqualByComparingTo("98.0000");
        assertThat(discount.modeledGrossProfit()).isNull();
        assertThat(discount.limitations()).anyMatch(item -> item.contains("Gross profit"));

        var coverage = BusinessAnalysisCalculations.scenario(
                ScenarioType.SALES_VELOCITY_CHANGE, new BigDecimal("20"), "USD",
                BigDecimal.ZERO, null, null, new BigDecimal("30"));
        assertThat(coverage.modeledDaysOfCover()).isEqualByComparingTo("25.0000");

        assertThatThrownBy(() -> BusinessAnalysisCalculations.scenario(
                ScenarioType.PRICE_CHANGE, new BigDecimal("1001"), "USD",
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ProductPeriodFacts facts(
            String sku, long previousQuantity, long currentQuantity, String previousRevenue, String currentRevenue) {
        return new ProductPeriodFacts(
                UUID.randomUUID(), sku, sku, "Category",
                new BigDecimal(previousRevenue), new BigDecimal(currentRevenue),
                previousQuantity, currentQuantity, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
