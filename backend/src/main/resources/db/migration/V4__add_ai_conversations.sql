CREATE TABLE ai_conversations (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_ai_conversations_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_ai_conversations_owner_updated
    ON ai_conversations (store_id, user_id, updated_at DESC);

CREATE TABLE ai_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversations (id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    response_payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ai_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_ai_messages_content_length CHECK (CHAR_LENGTH(content) BETWEEN 1 AND 10000)
);

CREATE INDEX idx_ai_messages_conversation_created
    ON ai_messages (conversation_id, created_at, id);

CREATE TABLE ai_tool_executions (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    conversation_id UUID NOT NULL REFERENCES ai_conversations (id) ON DELETE CASCADE,
    message_id UUID NOT NULL REFERENCES ai_messages (id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    tool_name VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    duration_ms BIGINT NOT NULL,
    input_metadata TEXT NOT NULL,
    error_code VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ai_tool_execution_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_ai_tool_execution_duration CHECK (duration_ms >= 0)
);

CREATE INDEX idx_ai_tool_executions_request
    ON ai_tool_executions (request_id, created_at);

CREATE INDEX idx_ai_tool_executions_owner
    ON ai_tool_executions (store_id, user_id, created_at DESC);
