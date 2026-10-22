CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_email_lowercase CHECK (email = LOWER(email)),
    CONSTRAINT ck_app_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE stores (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_stores_currency_uppercase CHECK (currency_code = UPPER(currency_code))
);

CREATE TABLE store_memberships (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_memberships_store_user UNIQUE (store_id, user_id),
    CONSTRAINT ck_store_memberships_role CHECK (role IN ('ADMIN', 'STAFF'))
);

CREATE INDEX idx_store_memberships_user_store
    ON store_memberships (user_id, store_id);
