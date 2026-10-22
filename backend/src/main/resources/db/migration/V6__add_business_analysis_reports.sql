CREATE TABLE business_analysis_reports (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores (id),
    created_by_user_id UUID NOT NULL REFERENCES app_users (id),
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    analysis_type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    schema_version INTEGER NOT NULL,
    report_version INTEGER NOT NULL,
    period_start DATE NOT NULL,
    period_end_exclusive DATE NOT NULL,
    comparison_period_start DATE NOT NULL,
    comparison_period_end_exclusive DATE NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    data_as_of TIMESTAMP WITH TIME ZONE,
    report_document JSONB,
    model_provider VARCHAR(40),
    model_name VARCHAR(160),
    prompt_version VARCHAR(80),
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    pricing_version VARCHAR(40),
    estimated_cost_usd NUMERIC(19,8),
    model_latency_ms BIGINT,
    failure_code VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    retention_until TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_business_reports_idempotency
        UNIQUE (store_id, created_by_user_id, idempotency_key),
    CONSTRAINT ck_business_reports_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_business_reports_type
        CHECK (analysis_type IN ('REVENUE_INVESTIGATION', 'INVENTORY_RISK', 'SCENARIO_ANALYSIS')),
    CONSTRAINT ck_business_reports_versions
        CHECK (schema_version > 0 AND report_version > 0),
    CONSTRAINT ck_business_reports_period
        CHECK (period_start < period_end_exclusive
            AND comparison_period_start < comparison_period_end_exclusive),
    CONSTRAINT ck_business_reports_document_state
        CHECK ((status = 'COMPLETED' AND report_document IS NOT NULL AND completed_at IS NOT NULL)
            OR status <> 'COMPLETED'),
    CONSTRAINT ck_business_reports_usage
        CHECK ((input_tokens IS NULL AND output_tokens IS NULL AND total_tokens IS NULL)
            OR (input_tokens >= 0 AND output_tokens >= 0
                AND total_tokens >= input_tokens + output_tokens)),
    CONSTRAINT ck_business_reports_cost
        CHECK (estimated_cost_usd IS NULL OR estimated_cost_usd >= 0),
    CONSTRAINT ck_business_reports_latency
        CHECK (model_latency_ms IS NULL OR model_latency_ms >= 0)
);

CREATE INDEX idx_business_reports_admin_list
    ON business_analysis_reports (store_id, created_at DESC, id);

CREATE INDEX idx_business_reports_staff_list
    ON business_analysis_reports (store_id, created_by_user_id, created_at DESC, id);

CREATE INDEX idx_business_reports_store_status
    ON business_analysis_reports (store_id, status, created_at DESC, id);
