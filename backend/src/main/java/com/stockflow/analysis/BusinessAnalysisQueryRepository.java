package com.stockflow.analysis;

import com.stockflow.analytics.AnalyticsPeriodResolver.ResolvedPeriod;
import com.stockflow.analysis.BusinessAnalysisModels.DimensionRevenue;
import com.stockflow.analysis.BusinessAnalysisModels.FinancialTotals;
import com.stockflow.analysis.BusinessAnalysisModels.ProductPeriodFacts;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class BusinessAnalysisQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    BusinessAnalysisQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<ProductPeriodFacts> productFacts(UUID storeId, ResolvedPeriod current, ResolvedPeriod previous) {
        var sql = """
                SELECT oi.product_id,
                       MAX(oi.sku_snapshot) AS sku,
                       MAX(oi.name_snapshot) AS name,
                       MAX(oi.category_snapshot) AS category,
                       COALESCE(SUM(CASE WHEN o.paid_at >= :previousStart AND o.paid_at < :previousEnd
                            THEN (oi.unit_price * oi.quantity) - oi.discount_amount ELSE 0 END), 0) AS previous_revenue,
                       COALESCE(SUM(CASE WHEN o.paid_at >= :currentStart AND o.paid_at < :currentEnd
                            THEN (oi.unit_price * oi.quantity) - oi.discount_amount ELSE 0 END), 0) AS current_revenue,
                       COALESCE(SUM(CASE WHEN o.paid_at >= :previousStart AND o.paid_at < :previousEnd
                            THEN oi.quantity ELSE 0 END), 0) AS previous_quantity,
                       COALESCE(SUM(CASE WHEN o.paid_at >= :currentStart AND o.paid_at < :currentEnd
                            THEN oi.quantity ELSE 0 END), 0) AS current_quantity,
                       COALESCE(SUM(CASE WHEN o.paid_at >= :previousStart AND o.paid_at < :previousEnd
                            THEN oi.unit_cost_snapshot * oi.quantity ELSE 0 END), 0) AS previous_cost,
                       COALESCE(SUM(CASE WHEN o.paid_at >= :currentStart AND o.paid_at < :currentEnd
                            THEN oi.unit_cost_snapshot * oi.quantity ELSE 0 END), 0) AS current_cost
                FROM orders o
                JOIN order_items oi ON oi.order_id = o.id
                WHERE o.store_id = :storeId
                  AND o.status = 'PAID'
                  AND ((o.paid_at >= :previousStart AND o.paid_at < :previousEnd)
                    OR (o.paid_at >= :currentStart AND o.paid_at < :currentEnd))
                GROUP BY oi.product_id
                ORDER BY oi.product_id
                """;
        return jdbc.query(sql, params(storeId, current, previous), (row, index) -> new ProductPeriodFacts(
                row.getObject("product_id", UUID.class), row.getString("sku"), row.getString("name"),
                row.getString("category"), row.getBigDecimal("previous_revenue"),
                row.getBigDecimal("current_revenue"), row.getLong("previous_quantity"),
                row.getLong("current_quantity"), row.getBigDecimal("previous_cost"),
                row.getBigDecimal("current_cost")));
    }

    List<DimensionRevenue> categoryRevenue(UUID storeId, ResolvedPeriod current, ResolvedPeriod previous) {
        var sql = """
                SELECT oi.category_snapshot AS category,
                       COALESCE(SUM(CASE WHEN o.paid_at >= :previousStart AND o.paid_at < :previousEnd
                            THEN (oi.unit_price * oi.quantity) - oi.discount_amount ELSE 0 END), 0) AS previous_revenue,
                       COALESCE(SUM(CASE WHEN o.paid_at >= :currentStart AND o.paid_at < :currentEnd
                            THEN (oi.unit_price * oi.quantity) - oi.discount_amount ELSE 0 END), 0) AS current_revenue
                FROM orders o
                JOIN order_items oi ON oi.order_id = o.id
                WHERE o.store_id = :storeId
                  AND o.status = 'PAID'
                  AND ((o.paid_at >= :previousStart AND o.paid_at < :previousEnd)
                    OR (o.paid_at >= :currentStart AND o.paid_at < :currentEnd))
                GROUP BY oi.category_snapshot
                ORDER BY oi.category_snapshot
                """;
        return jdbc.query(sql, params(storeId, current, previous), (row, index) -> new DimensionRevenue(
                row.getString("category"), row.getString("category"),
                row.getBigDecimal("previous_revenue"), row.getBigDecimal("current_revenue")));
    }

    List<InventoryPeriodRow> inventoryFacts(UUID storeId, ResolvedPeriod current, ResolvedPeriod previous) {
        var sql = """
                WITH sales AS (
                    SELECT oi.product_id,
                           COALESCE(SUM(CASE WHEN o.paid_at >= :previousStart AND o.paid_at < :previousEnd
                                THEN oi.quantity ELSE 0 END), 0) AS previous_units,
                           COALESCE(SUM(CASE WHEN o.paid_at >= :currentStart AND o.paid_at < :currentEnd
                                THEN oi.quantity ELSE 0 END), 0) AS current_units
                    FROM orders o
                    JOIN order_items oi ON oi.order_id = o.id
                    WHERE o.store_id = :storeId
                      AND o.status = 'PAID'
                      AND ((o.paid_at >= :previousStart AND o.paid_at < :previousEnd)
                        OR (o.paid_at >= :currentStart AND o.paid_at < :currentEnd))
                    GROUP BY oi.product_id
                )
                SELECT p.id AS product_id, p.sku, p.name, p.category,
                       i.available_quantity, i.reserved_quantity, i.reorder_point,
                       COALESCE(s.previous_units, 0) AS previous_units,
                       COALESCE(s.current_units, 0) AS current_units
                FROM products p
                JOIN inventory i ON i.product_id = p.id AND i.store_id = p.store_id
                LEFT JOIN sales s ON s.product_id = p.id
                WHERE p.store_id = :storeId AND p.status = 'ACTIVE'
                ORDER BY p.name, p.id
                """;
        return jdbc.query(sql, params(storeId, current, previous), (row, index) -> new InventoryPeriodRow(
                row.getObject("product_id", UUID.class), row.getString("sku"), row.getString("name"),
                row.getString("category"), row.getLong("available_quantity"),
                row.getLong("reserved_quantity"), row.getLong("reorder_point"),
                row.getLong("previous_units"), row.getLong("current_units")));
    }

    FinancialTotals financialTotals(UUID storeId, ResolvedPeriod period) {
        var sql = """
                SELECT COALESCE(SUM((oi.unit_price * oi.quantity) - oi.discount_amount), 0) AS revenue,
                       COALESCE(SUM(oi.unit_cost_snapshot * oi.quantity), 0) AS cogs,
                       COALESCE(SUM(((oi.unit_price * oi.quantity) - oi.discount_amount)
                            - (oi.unit_cost_snapshot * oi.quantity)), 0) AS gross_profit,
                       COALESCE(SUM(oi.discount_amount), 0) AS discounts,
                       COALESCE(SUM(oi.quantity), 0) AS units_sold
                FROM orders o
                JOIN order_items oi ON oi.order_id = o.id
                WHERE o.store_id = :storeId
                  AND o.status = 'PAID'
                  AND o.paid_at >= :currentStart
                  AND o.paid_at < :currentEnd
                """;
        var values = new MapSqlParameterSource()
                .addValue("storeId", storeId)
                .addValue("currentStart", Timestamp.from(period.startInstant()))
                .addValue("currentEnd", Timestamp.from(period.endInstant()));
        return jdbc.queryForObject(sql, values, (row, index) -> new FinancialTotals(
                row.getBigDecimal("revenue"), row.getBigDecimal("cogs"), row.getBigDecimal("gross_profit"),
                row.getBigDecimal("discounts"), row.getLong("units_sold")));
    }

    private MapSqlParameterSource params(UUID storeId, ResolvedPeriod current, ResolvedPeriod previous) {
        return new MapSqlParameterSource()
                .addValue("storeId", storeId)
                .addValue("currentStart", Timestamp.from(current.startInstant()))
                .addValue("currentEnd", Timestamp.from(current.endInstant()))
                .addValue("previousStart", Timestamp.from(previous.startInstant()))
                .addValue("previousEnd", Timestamp.from(previous.endInstant()));
    }

    record InventoryPeriodRow(
            UUID productId,
            String sku,
            String name,
            String category,
            long availableQuantity,
            long reservedQuantity,
            long reorderPoint,
            long previousUnits,
            long currentUnits) {
    }
}
