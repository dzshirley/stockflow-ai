package com.stockflow.analysis;

import java.math.BigDecimal;
import java.util.List;

/** Server-built chart data; browsers never parse model prose into values. */
public record BusinessChart(
        String id,
        ChartType type,
        String title,
        String categoryLabel,
        String valueLabel,
        String unit,
        List<Point> points) {

    public BusinessChart {
        points = points == null ? List.of() : List.copyOf(points);
    }

    public enum ChartType { BAR, WATERFALL }

    public record Point(String key, String label, BigDecimal value) {
    }
}
