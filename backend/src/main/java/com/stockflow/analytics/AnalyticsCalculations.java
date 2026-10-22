package com.stockflow.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class AnalyticsCalculations {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private AnalyticsCalculations() {
    }

    static BigDecimal averageOrderValue(BigDecimal revenue, long orderCount) {
        return orderCount == 0
                ? null
                : revenue.divide(BigDecimal.valueOf(orderCount), 4, RoundingMode.HALF_UP);
    }

    static BigDecimal growthPercent(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) return null;
        return current.subtract(previous)
                .divide(previous, 6, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal dailyVelocity(long unitsSold, long days) {
        return BigDecimal.valueOf(unitsSold)
                .divide(BigDecimal.valueOf(days), 4, RoundingMode.HALF_UP);
    }

    static BigDecimal daysOfCover(long availableUnits, BigDecimal dailyVelocity) {
        if (dailyVelocity.compareTo(BigDecimal.ZERO) == 0) return null;
        return BigDecimal.valueOf(availableUnits).divide(dailyVelocity, 2, RoundingMode.HALF_UP);
    }
}
