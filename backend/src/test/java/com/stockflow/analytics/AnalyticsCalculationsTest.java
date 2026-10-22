package com.stockflow.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AnalyticsCalculationsTest {

    @Test
    void calculatesAverageAndGrowthWithDecimalPrecision() {
        assertThat(AnalyticsCalculations.averageOrderValue(new BigDecimal("25.0000"), 2))
                .isEqualByComparingTo("12.5000");
        assertThat(AnalyticsCalculations.growthPercent(
                        new BigDecimal("125.00"), new BigDecimal("100.00")))
                .isEqualByComparingTo("25.00");
    }

    @Test
    void returnsUnavailableForZeroDenominators() {
        assertThat(AnalyticsCalculations.averageOrderValue(BigDecimal.ZERO, 0)).isNull();
        assertThat(AnalyticsCalculations.growthPercent(BigDecimal.TEN, BigDecimal.ZERO)).isNull();
        assertThat(AnalyticsCalculations.daysOfCover(10, BigDecimal.ZERO)).isNull();
    }

    @Test
    void calculatesSalesVelocityAndCoverage() {
        var velocity = AnalyticsCalculations.dailyVelocity(30, 30);

        assertThat(velocity).isEqualByComparingTo("1.0000");
        assertThat(AnalyticsCalculations.daysOfCover(12, velocity)).isEqualByComparingTo("12.00");
    }
}
