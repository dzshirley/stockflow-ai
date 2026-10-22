\set ON_ERROR_STOP on

-- Required psql variable: store_id. Deletes only records created by analytics-demo.sql.
BEGIN;

DELETE FROM orders
WHERE store_id = :'store_id'::uuid
  AND id IN (
      SELECT DISTINCT oi.order_id
      FROM order_items oi
      WHERE oi.sku_snapshot LIKE 'DEMO-%'
  );

DELETE FROM products
WHERE store_id = :'store_id'::uuid
  AND sku LIKE 'DEMO-%';

COMMIT;

\echo 'Removed analytics demo records for store' :store_id
