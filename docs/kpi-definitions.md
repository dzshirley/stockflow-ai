# KPI Definitions

These definitions are the Phase 4 source of truth for both REST responses and the React dashboard. The future AI analyst must call the same deterministic service methods rather than recalculate these values.

## Global reporting contract

- **Population:** only orders with `status = PAID` and a non-null `paid_at` qualify for sales metrics. Pending, failed, cancelled, and expired orders are excluded.
- **Business date:** `orders.paid_at`, not order creation or last-update time.
- **Intervals:** store-local calendar dates are converted to UTC instants and queried as `[startTimestamp, endTimestamp)`. The response always returns the two dates and IANA store time zone.
- **Presets:** last 7/30/90 days include the store's current calendar day; this month is month-to-date; last month is one complete calendar month; custom ranges require both boundaries. Ranges are limited to 366 days.
- **Comparison:** the immediately preceding equal number of local calendar days is used except `LAST_MONTH`, which compares the previous complete calendar month. A zero prior-period revenue returns `comparable=false` and a null percentage.
- **Currency:** one store currency is supported. Any qualifying order with a different currency causes `ANALYTICS_CURRENCY_MISMATCH`; currencies are never silently mixed or converted.
- **Precision:** PostgreSQL `NUMERIC(19,4)` and Java `BigDecimal` are authoritative. Money remains scale 4 in calculations and is rounded only for display. Growth percent is rounded to two decimals.
- **Missing data:** additive totals are zero for an empty period. Ratios with a zero denominator are null, not zero. Trend APIs return explicit zero buckets within the requested interval.
- **Refunds:** no refund model exists. The current revenue metric must be labeled **paid sales revenue**, not net revenue. Cancellation before payment is excluded; paid-order refunds cannot yet be represented.

## Supported sales metrics

| Metric | Business definition and formula | Source and exclusions | Unit / null behavior |
|---|---|---|---|
| Paid sales revenue | `SUM(orders.total)` for qualifying paid orders | `orders`; uses `paid_at`; excludes every non-PAID status. `total` is after modeled line discounts and before unmodeled refund, tax, shipping, or fees. | Store currency; `0.0000` when empty |
| Paid order count | `COUNT(orders.id)` at one row per qualifying order | `orders`; no item join is used for this count, preventing multi-line duplication | Orders; zero when empty |
| Average order value | `paid sales revenue / paid order count` | Same population as revenue and count | Store currency; null when count is zero |
| Units sold | `SUM(order_items.quantity)` for qualifying orders | `orders` joined to `order_items`; positive quantities are enforced by the database | Units; zero when empty |
| Revenue change | `current revenue - previous revenue` | Two non-overlapping periods with identical definitions | Store currency |
| Revenue growth | `(current revenue - previous revenue) / previous revenue * 100` | Same as revenue change | Percent; null and non-comparable when previous revenue is zero |
| Product revenue | `SUM(unit_price * quantity - discount_amount)` grouped by `product_id` | Historical `sku_snapshot`, `name_snapshot`, and `unit_price`; never current list price | Store currency; products with no qualifying sales are absent |
| Category revenue | Same line revenue grouped by `category_snapshot` | Historical category snapshot on each order item | Store currency; categories with no qualifying sales are absent |

`discount_amount` is a line-total discount. Phase 2 currently writes zero because discount commands are not implemented. `orders.total` remains the revenue source of truth at overview/trend grain; product and category totals use line snapshots and are expected to reconcile while no order-level-only discounts exist.

## Inventory metrics

Inventory stock is a current snapshot; sales velocity uses the selected sales period.

| Metric | Definition | Source | Null / limitations |
|---|---|---|---|
| Available units | `SUM(inventory.available_quantity)` for active products | `inventory` + `products` | Reserved units are not included |
| Reserved units | `SUM(inventory.reserved_quantity)` for active products | `inventory` + `products` | Reported separately from available |
| Low-stock products | Count where `available_quantity <= reorder_point` | Current inventory rows for active products | Operational count, not historical stockout time |
| Sales velocity | `units sold in selected period / local calendar days in period` | Paid order items | Units/day; zero when no sales |
| Days of cover | `available_quantity / average daily units sold` | Current available stock and selected-period velocity | Null when velocity is zero; reserved units are excluded |
| Inventory risk | `OUT_OF_STOCK` when available is 0; else `LOW_STOCK` when available is at/below reorder point; else `AT_RISK` when cover is at/below 14 days; else `NO_SALES` or `HEALTHY` | Current inventory plus sales velocity | A prioritization rule, not a forecast or causal claim |

True inventory turnover requires average historical inventory or cost of goods sold. Historical stockout duration requires inventory-event history at a suitable grain. Neither is claimed in Phase 4.

## Snapshot and migration behavior

Flyway V3 adds immutable `paid_at`, `category_snapshot`, and `unit_cost_snapshot`. New orders populate these at payment/order creation. Existing paid orders are backfilled with their previous terminal `updated_at`, and existing item category/cost values are backfilled from the catalog value at migration time. Consequently, pre-V3 category and cost history may reflect the value at migration, not the value at the original sale. Product price history was already preserved by `unit_price`.

Gross profit is intentionally not exposed in Phase 4. Although new lines now preserve unit cost, the migrated historical cost snapshots cannot be proven historically accurate and refunds/allocated order discounts remain unmodeled.
