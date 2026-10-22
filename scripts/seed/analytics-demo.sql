\set ON_ERROR_STOP on

-- Required psql variables: store_id and user_id. The target store/user must already exist.
-- This inserts only DEMO-* products and deterministic IDs scoped to the selected store.
BEGIN;

INSERT INTO products (id, store_id, sku, name, category, price, unit_cost, status, created_at, updated_at)
SELECT md5(:'store_id' || '-product-' || seed.n)::uuid,
       :'store_id'::uuid,
       'DEMO-' || LPAD(seed.n::text, 3, '0'),
       seed.name,
       seed.category,
       seed.price,
       seed.cost,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM (VALUES
    (1, 'Atlas Pour-Over', 'Brewing', 48.00::numeric, 22.00::numeric),
    (2, 'Harbor Grinder', 'Brewing', 86.00::numeric, 41.00::numeric),
    (3, 'Northstar Beans', 'Coffee', 18.00::numeric, 7.50::numeric),
    (4, 'Daybreak Beans', 'Coffee', 16.00::numeric, 6.80::numeric),
    (5, 'Studio Mug', 'Drinkware', 24.00::numeric, 9.00::numeric),
    (6, 'Field Tumbler', 'Drinkware', 32.00::numeric, 13.00::numeric)
) AS seed(n, name, category, price, cost)
ON CONFLICT (store_id, sku) DO NOTHING;

INSERT INTO inventory (
    product_id, store_id, available_quantity, reserved_quantity, reorder_point, version, updated_at)
SELECT p.id,
       p.store_id,
       CASE p.sku
           WHEN 'DEMO-001' THEN 3
           WHEN 'DEMO-002' THEN 0
           WHEN 'DEMO-003' THEN 12
           WHEN 'DEMO-004' THEN 45
           WHEN 'DEMO-005' THEN 7
           ELSE 30
       END,
       CASE WHEN p.sku = 'DEMO-003' THEN 4 ELSE 0 END,
       CASE WHEN p.sku IN ('DEMO-001', 'DEMO-002') THEN 5 ELSE 8 END,
       0,
       CURRENT_TIMESTAMP
FROM products p
WHERE p.store_id = :'store_id'::uuid AND p.sku LIKE 'DEMO-%'
ON CONFLICT (product_id) DO NOTHING;

WITH generated AS (
    SELECT n,
           md5(:'store_id' || '-order-' || n)::uuid AS order_id,
           md5(:'store_id' || '-product-' || ((n % 6) + 1))::uuid AS product_id,
           ((n % 3) + 1)::bigint AS quantity,
           (CURRENT_DATE - n)::timestamp AT TIME ZONE 'UTC' + INTERVAL '15 hours' AS occurred_at,
           CASE WHEN n % 17 = 0 THEN 'CANCELLED' ELSE 'PAID' END AS status
    FROM generate_series(0, 119) AS series(n)
)
INSERT INTO orders (
    id, store_id, status, subtotal, discount_total, total, currency_code, expires_at,
    created_by, created_at, updated_at, paid_at, version)
SELECT g.order_id,
       :'store_id'::uuid,
       g.status,
       p.price * g.quantity,
       0,
       p.price * g.quantity,
       s.currency_code,
       g.occurred_at + INTERVAL '15 minutes',
       :'user_id'::uuid,
       g.occurred_at,
       g.occurred_at + INTERVAL '2 minutes',
       CASE WHEN g.status = 'PAID' THEN g.occurred_at + INTERVAL '2 minutes' ELSE NULL END,
       0
FROM generated g
JOIN products p ON p.id = g.product_id AND p.store_id = :'store_id'::uuid
JOIN stores s ON s.id = :'store_id'::uuid
ON CONFLICT (id) DO NOTHING;

WITH generated AS (
    SELECT n,
           md5(:'store_id' || '-order-' || n)::uuid AS order_id,
           md5(:'store_id' || '-item-' || n)::uuid AS item_id,
           md5(:'store_id' || '-product-' || ((n % 6) + 1))::uuid AS product_id,
           ((n % 3) + 1)::bigint AS quantity
    FROM generate_series(0, 119) AS series(n)
)
INSERT INTO order_items (
    id, order_id, product_id, sku_snapshot, name_snapshot, category_snapshot,
    quantity, unit_price, unit_cost_snapshot, discount_amount)
SELECT g.item_id, g.order_id, g.product_id, p.sku, p.name, p.category,
       g.quantity, p.price, p.unit_cost, 0
FROM generated g
JOIN orders o ON o.id = g.order_id AND o.store_id = :'store_id'::uuid
JOIN products p ON p.id = g.product_id AND p.store_id = :'store_id'::uuid
ON CONFLICT (order_id, product_id) DO NOTHING;

COMMIT;

\echo 'Seeded six DEMO-* products and 120 deterministic orders for store' :store_id
