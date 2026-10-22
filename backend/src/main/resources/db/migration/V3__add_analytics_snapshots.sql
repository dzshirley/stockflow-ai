ALTER TABLE orders
    ADD COLUMN paid_at TIMESTAMP WITH TIME ZONE;

UPDATE orders
SET paid_at = updated_at
WHERE status = 'PAID';

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_paid_timestamp
        CHECK ((status = 'PAID' AND paid_at IS NOT NULL) OR (status <> 'PAID' AND paid_at IS NULL));

ALTER TABLE order_items
    ADD COLUMN category_snapshot VARCHAR(120);

ALTER TABLE order_items
    ADD COLUMN unit_cost_snapshot NUMERIC(19,4);

UPDATE order_items oi
SET category_snapshot = p.category,
    unit_cost_snapshot = p.unit_cost
FROM products p
WHERE p.id = oi.product_id;

ALTER TABLE order_items
    ALTER COLUMN category_snapshot SET NOT NULL;

ALTER TABLE order_items
    ALTER COLUMN unit_cost_snapshot SET NOT NULL;

ALTER TABLE order_items
    ADD CONSTRAINT ck_order_items_cost_nonnegative CHECK (unit_cost_snapshot >= 0);

CREATE INDEX idx_orders_store_status_paid_at
    ON orders (store_id, status, paid_at);

CREATE INDEX idx_order_items_order_analytics
    ON order_items (order_id, product_id);
