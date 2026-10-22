ALTER TABLE stores
    ALTER COLUMN currency_code TYPE VARCHAR(3);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    sku VARCHAR(80) NOT NULL,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(120) NOT NULL,
    price NUMERIC(19,4) NOT NULL,
    unit_cost NUMERIC(19,4) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_products_store_sku UNIQUE (store_id, sku),
    CONSTRAINT uq_products_store_id UNIQUE (store_id, id),
    CONSTRAINT ck_products_sku_uppercase CHECK (sku = UPPER(sku)),
    CONSTRAINT ck_products_price_nonnegative CHECK (price >= 0),
    CONSTRAINT ck_products_cost_nonnegative CHECK (unit_cost >= 0),
    CONSTRAINT ck_products_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_products_store_status_name
    ON products (store_id, status, name);
CREATE INDEX idx_products_store_category
    ON products (store_id, category);

CREATE TABLE inventory (
    product_id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    available_quantity BIGINT NOT NULL DEFAULT 0,
    reserved_quantity BIGINT NOT NULL DEFAULT 0,
    reorder_point BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_inventory_store_product
        FOREIGN KEY (store_id, product_id) REFERENCES products (store_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_inventory_available_nonnegative CHECK (available_quantity >= 0),
    CONSTRAINT ck_inventory_reserved_nonnegative CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_inventory_reorder_nonnegative CHECK (reorder_point >= 0)
);

CREATE INDEX idx_inventory_store_low_stock
    ON inventory (store_id, available_quantity, reorder_point);

CREATE TABLE inventory_adjustments (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    available_delta BIGINT NOT NULL,
    reserved_delta BIGINT NOT NULL DEFAULT 0,
    reason VARCHAR(300) NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES app_users (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_inventory_adjustment_nonzero
        CHECK (available_delta <> 0 OR reserved_delta <> 0)
);

CREATE INDEX idx_inventory_adjustments_store_product_created
    ON inventory_adjustments (store_id, product_id, created_at DESC);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    subtotal NUMERIC(19,4) NOT NULL,
    discount_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    total NUMERIC(19,4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID NOT NULL REFERENCES app_users (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_orders_store_id UNIQUE (store_id, id),
    CONSTRAINT ck_orders_status
        CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'PAYMENT_FAILED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_orders_amounts_nonnegative
        CHECK (subtotal >= 0 AND discount_total >= 0 AND total >= 0),
    CONSTRAINT ck_orders_currency_uppercase CHECK (currency_code = UPPER(currency_code))
);

CREATE INDEX idx_orders_store_status_created
    ON orders (store_id, status, created_at DESC);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id),
    sku_snapshot VARCHAR(80) NOT NULL,
    name_snapshot VARCHAR(200) NOT NULL,
    quantity BIGINT NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    CONSTRAINT uq_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_items_amounts_nonnegative
        CHECK (unit_price >= 0 AND discount_amount >= 0)
);

CREATE TABLE order_idempotency_keys (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    order_id UUID REFERENCES orders (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_order_idempotency_store_key UNIQUE (store_id, idempotency_key)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_events (published_at, created_at);
