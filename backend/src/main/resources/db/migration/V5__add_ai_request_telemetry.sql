CREATE TABLE ai_request_telemetry (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    conversation_id UUID NOT NULL REFERENCES ai_conversations (id) ON DELETE CASCADE,
    message_id UUID NOT NULL REFERENCES ai_messages (id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    model VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    estimated_cost_usd NUMERIC(19,8),
    pricing_version VARCHAR(40),
    tool_call_count INTEGER NOT NULL,
    duration_ms BIGINT NOT NULL,
    error_code VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_ai_request_telemetry_request UNIQUE (request_id),
    CONSTRAINT ck_ai_request_telemetry_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_ai_request_telemetry_tokens CHECK (
        (input_tokens IS NULL AND output_tokens IS NULL AND total_tokens IS NULL)
        OR (input_tokens >= 0 AND output_tokens >= 0 AND total_tokens >= input_tokens + output_tokens)
    ),
    CONSTRAINT ck_ai_request_telemetry_cost CHECK (estimated_cost_usd IS NULL OR estimated_cost_usd >= 0),
    CONSTRAINT ck_ai_request_telemetry_counts CHECK (tool_call_count >= 0 AND duration_ms >= 0)
);

CREATE INDEX idx_ai_request_telemetry_owner_created
    ON ai_request_telemetry (store_id, user_id, created_at DESC);

CREATE INDEX idx_ai_request_telemetry_status_created
    ON ai_request_telemetry (status, created_at DESC);
