package com.stockflow.analytics;

import com.stockflow.analytics.AnalyticsPeriodResolver.ResolvedPeriod;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AnalyticsQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    AnalyticsQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    long currencyMismatchCount(UUID storeId, ResolvedPeriod period, String currency) {
        var sql = """
                SELECT COUNT(*)
                FROM orders
                WHERE store_id = :storeId
                  AND status = 'PAID'
                  AND paid_at >= :startAt
                  AND paid_at < :endAt
                  AND currency_code <> :currency
                """;
        return jdbc.queryForObject(
                sql,
                periodParams(storeId, period).addValue("currency", currency),
                Long.class);
    }

    SalesAggregate sales(UUID storeId, ResolvedPeriod period) {
        var sql = """
                WITH qualifying_orders AS (
                    SELECT id, total
                    FROM orders
                    WHERE store_id = :storeId
                      AND status = 'PAID'
                      AND paid_at >= :startAt
                      AND paid_at < :endAt
                ), item_totals AS (
                    SELECT COALESCE(SUM(oi.quantity), 0) AS units_sold
                    FROM qualifying_orders qo
                    JOIN order_items oi ON oi.order_id = qo.id
                )
                SELECT COALESCE(SUM(qo.total), 0) AS revenue,
                       COUNT(qo.id) AS order_count,
                       (SELECT units_sold FROM item_totals) AS units_sold
                FROM qualifying_orders qo
                """;
        return jdbc.queryForObject(sql, periodParams(storeId, period), (row, index) -> new SalesAggregate(
                row.getBigDecimal("revenue"),
                row.getLong("order_count"),
                row.getLong("units_sold")));
    }

    List<TrendAggregate> revenueTrend(
            UUID storeId, ResolvedPeriod period, AnalyticsModels.TrendInterval interval) {
        var bucket = switch (interval) {
            case DAY -> "day";
            case WEEK -> "week";
            case MONTH -> "month";
        };
        var sql = """
                WITH order_facts AS (
                    SELECT o.id, o.paid_at, o.total,
                           COALESCE(SUM(oi.quantity), 0) AS units_sold
                    FROM orders o
                    JOIN order_items oi ON oi.order_id = o.id
                    WHERE o.store_id = :storeId
                      AND o.status = 'PAID'
                      AND o.paid_at >= :startAt
                      AND o.paid_at < :endAt
                    GROUP BY o.id, o.paid_at, o.total
                )
                SELECT date_trunc('%s', paid_at AT TIME ZONE :timeZone)::date AS bucket_start,
                       COALESCE(SUM(total), 0) AS revenue,
                       COUNT(id) AS order_count,
                       COALESCE(SUM(units_sold), 0) AS units_sold
                FROM order_facts
                GROUP BY bucket_start
                ORDER BY bucket_start
                """.formatted(bucket);
        var params = periodParams(storeId, period).addValue("timeZone", period.zone().getId());
        return jdbc.query(sql, params, (row, index) -> new TrendAggregate(
                row.getObject("bucket_start", LocalDate.class),
                row.getBigDecimal("revenue"),
                row.getLong("order_count"),
                row.getLong("units_sold")));
    }

    List<AnalyticsModels.ProductPerformance> topProducts(
            UUID storeId, ResolvedPeriod period, int limit) {
        var sql = """
                SELECT oi.product_id,
                       MAX(oi.sku_snapshot) AS sku,
                       MAX(oi.name_snapshot) AS name,
                       SUM((oi.unit_price * oi.quantity) - oi.discount_amount) AS revenue,
                       SUM(oi.quantity) AS units_sold,
                       COUNT(DISTINCT o.id) AS order_count
                FROM orders o
                JOIN order_items oi ON oi.order_id = o.id
                WHERE o.store_id = :storeId
                  AND o.status = 'PAID'
                  AND o.paid_at >= :startAt
                  AND o.paid_at < :endAt
                GROUP BY oi.product_id
                ORDER BY revenue DESC, units_sold DESC, name ASC
                LIMIT :limit
                """;
        var params = periodParams(storeId, period).addValue("limit", limit);
        return jdbc.query(sql, params, (row, index) -> new AnalyticsModels.ProductPerformance(
                row.getObject("product_id", UUID.class),
                row.getString("sku"),
                row.getString("name"),
                row.getBigDecimal("revenue"),
                row.getLong("units_sold"),
                row.getLong("order_count")));
    }

    List<AnalyticsModels.CategoryPerformance> categories(UUID storeId, ResolvedPeriod period) {
        var sql = """
                SELECT oi.category_snapshot AS category,
                       SUM((oi.unit_price * oi.quantity) - oi.discount_amount) AS revenue,
                       SUM(oi.quantity) AS units_sold,
                       COUNT(DISTINCT o.id) AS order_count
                FROM orders o
                JOIN order_items oi ON oi.order_id = o.id
                WHERE o.store_id = :storeId
                  AND o.status = 'PAID'
                  AND o.paid_at >= :startAt
                  AND o.paid_at < :endAt
                GROUP BY oi.category_snapshot
                ORDER BY revenue DESC, category ASC
                """;
        return jdbc.query(sql, periodParams(storeId, period), (row, index) ->
                new AnalyticsModels.CategoryPerformance(
                        row.getString("category"),
                        row.getBigDecimal("revenue"),
                        row.getLong("units_sold"),
                        row.getLong("order_count")));
    }

    InventoryAggregate inventorySummary(UUID storeId, ResolvedPeriod period) {
        var sql = """
                WITH stock AS (
                    SELECT COUNT(*) AS active_products,
                           COALESCE(SUM(i.available_quantity), 0) AS available_units,
                           COALESCE(SUM(i.reserved_quantity), 0) AS reserved_units,
                           COUNT(*) FILTER (WHERE i.available_quantity <= i.reorder_point) AS low_stock_products
                    FROM inventory i
                    JOIN products p ON p.id = i.product_id AND p.store_id = i.store_id
                    WHERE i.store_id = :storeId AND p.status = 'ACTIVE'
                ), sales AS (
                    SELECT COALESCE(SUM(oi.quantity), 0) AS units_sold
                    FROM orders o
                    JOIN order_items oi ON oi.order_id = o.id
                    WHERE o.store_id = :storeId
                      AND o.status = 'PAID'
                      AND o.paid_at >= :startAt
                      AND o.paid_at < :endAt
                )
                SELECT stock.*, sales.units_sold FROM stock CROSS JOIN sales
                """;
        return jdbc.queryForObject(sql, periodParams(storeId, period), (row, index) -> new InventoryAggregate(
                row.getLong("active_products"),
                row.getLong("available_units"),
                row.getLong("reserved_units"),
                row.getLong("low_stock_products"),
                row.getLong("units_sold")));
    }

    List<InventoryRiskRow> inventoryRisk(UUID storeId, ResolvedPeriod period, int limit) {
        var sql = """
                WITH sales AS (
                    SELECT oi.product_id, SUM(oi.quantity) AS units_sold
                    FROM orders o
                    JOIN order_items oi ON oi.order_id = o.id
                    WHERE o.store_id = :storeId
                      AND o.status = 'PAID'
                      AND o.paid_at >= :startAt
                      AND o.paid_at < :endAt
                    GROUP BY oi.product_id
                )
                SELECT p.id AS product_id, p.sku, p.name, p.category,
                       i.available_quantity, i.reserved_quantity, i.reorder_point,
                       COALESCE(s.units_sold, 0) AS units_sold
                FROM products p
                JOIN inventory i ON i.product_id = p.id AND i.store_id = p.store_id
                LEFT JOIN sales s ON s.product_id = p.id
                WHERE p.store_id = :storeId AND p.status = 'ACTIVE'
                ORDER BY
                    CASE
                        WHEN i.available_quantity = 0 THEN 0
                        WHEN i.available_quantity <= i.reorder_point THEN 1
                        WHEN COALESCE(s.units_sold, 0) > 0
                             AND i.available_quantity / (s.units_sold::numeric / :dayCount) <= 14 THEN 2
                        WHEN COALESCE(s.units_sold, 0) = 0 THEN 4
                        ELSE 3
                    END,
                    CASE WHEN COALESCE(s.units_sold, 0) > 0
                         THEN i.available_quantity / (s.units_sold::numeric / :dayCount)
                         ELSE NULL END NULLS LAST,
                    p.name
                LIMIT :limit
                """;
        var params = periodParams(storeId, period)
                .addValue("dayCount", period.dayCount())
                .addValue("limit", limit);
        return jdbc.query(sql, params, (row, index) -> new InventoryRiskRow(
                row.getObject("product_id", UUID.class),
                row.getString("sku"),
                row.getString("name"),
                row.getString("category"),
                row.getLong("available_quantity"),
                row.getLong("reserved_quantity"),
                row.getLong("reorder_point"),
                row.getLong("units_sold")));
    }

    private MapSqlParameterSource periodParams(UUID storeId, ResolvedPeriod period) {
        return new MapSqlParameterSource()
                .addValue("storeId", storeId)
                .addValue("startAt", Timestamp.from(period.startInstant()))
                .addValue("endAt", Timestamp.from(period.endInstant()));
    }

    record SalesAggregate(BigDecimal revenue, long orders, long unitsSold) {
    }

    record TrendAggregate(LocalDate bucketStart, BigDecimal revenue, long orders, long unitsSold) {
    }

    record InventoryAggregate(
            long activeProducts,
            long availableUnits,
            long reservedUnits,
            long lowStockProducts,
            long unitsSold) {
    }

    record InventoryRiskRow(
            UUID productId,
            String sku,
            String name,
            String category,
            long availableQuantity,
            long reservedQuantity,
            long reorderPoint,
            long unitsSold) {
    }
}
