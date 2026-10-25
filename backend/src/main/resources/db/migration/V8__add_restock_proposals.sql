ALTER TABLE inventory_adjustments
    ADD COLUMN operation_command_id UUID;

CREATE UNIQUE INDEX uq_inventory_adjustments_operation_command
    ON inventory_adjustments (operation_command_id);

CREATE TABLE restock_proposals (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    created_by_user_id UUID NOT NULL REFERENCES app_users (id),
    create_idempotency_key VARCHAR(200) NOT NULL,
    create_request_hash VARCHAR(64) NOT NULL,
    product_id UUID NOT NULL REFERENCES products (id),
    source_report_id UUID NOT NULL REFERENCES business_analysis_reports (id),
    source_report_version INTEGER NOT NULL,
    recommendation_id UUID NOT NULL,
    evidence_id UUID NOT NULL,
    evidence_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    proposed_quantity BIGINT NOT NULL,
    rationale VARCHAR(1000) NOT NULL,
    assumptions VARCHAR(2000) NOT NULL,
    snapshot_available BIGINT NOT NULL,
    snapshot_reserved BIGINT NOT NULL,
    snapshot_reorder_point BIGINT NOT NULL,
    snapshot_inventory_version BIGINT NOT NULL,
    snapshot_inventory_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source_data_as_of TIMESTAMP WITH TIME ZONE NOT NULL,
    model_provider VARCHAR(40),
    model_name VARCHAR(160),
    approved_by_user_id UUID REFERENCES app_users (id),
    approved_at TIMESTAMP WITH TIME ZONE,
    approved_proposal_version BIGINT,
    decision_reason VARCHAR(500),
    execution_command_id UUID,
    execution_owner UUID,
    execution_started_at TIMESTAMP WITH TIME ZONE,
    execution_lease_until TIMESTAMP WITH TIME ZONE,
    inventory_adjustment_id UUID REFERENCES inventory_adjustments (id),
    executed_at TIMESTAMP WITH TIME ZONE,
    failure_code VARCHAR(80),
    stale_reason VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_restock_create_key_product
        UNIQUE (store_id, created_by_user_id, create_idempotency_key, product_id),
    CONSTRAINT uq_restock_execution_command UNIQUE (execution_command_id),
    CONSTRAINT ck_restock_status CHECK (status IN (
        'PROPOSED', 'APPROVED', 'EXECUTING', 'EXECUTED',
        'REJECTED', 'CANCELLED', 'STALE', 'FAILED')),
    CONSTRAINT ck_restock_quantity CHECK (proposed_quantity > 0 AND proposed_quantity <= 1000000),
    CONSTRAINT ck_restock_snapshot CHECK (
        snapshot_available >= 0 AND snapshot_reserved >= 0 AND snapshot_reorder_point >= 0
        AND snapshot_inventory_version >= 0),
    CONSTRAINT ck_restock_execution_lease CHECK (
        (status = 'EXECUTING' AND execution_command_id IS NOT NULL AND execution_owner IS NOT NULL
            AND execution_started_at IS NOT NULL AND execution_lease_until > execution_started_at)
        OR status <> 'EXECUTING')
);

CREATE INDEX idx_restock_admin_list
    ON restock_proposals (store_id, created_at DESC, id);
CREATE INDEX idx_restock_staff_list
    ON restock_proposals (store_id, created_by_user_id, created_at DESC, id);
CREATE INDEX idx_restock_source
    ON restock_proposals (store_id, source_report_id, recommendation_id);
CREATE INDEX idx_restock_recovery
    ON restock_proposals (status, execution_lease_until, id);

CREATE TABLE restock_proposal_events (
    id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES restock_proposals (id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor_user_id UUID REFERENCES app_users (id),
    actor_role VARCHAR(20) NOT NULL,
    reason_code VARCHAR(80),
    request_id UUID NOT NULL,
    proposal_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_restock_events_proposal_created
    ON restock_proposal_events (store_id, proposal_id, created_at, id);

CREATE TABLE restock_execution_attempts (
    id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES restock_proposals (id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    command_id UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    owner_id UUID NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_until TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    inventory_adjustment_id UUID REFERENCES inventory_adjustments (id),
    error_code VARCHAR(80),
    CONSTRAINT uq_restock_attempt_command UNIQUE (store_id, proposal_id, command_id),
    CONSTRAINT ck_restock_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT ck_restock_attempt_status CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED', 'ABANDONED'))
);

CREATE INDEX idx_restock_attempts_proposal_started
    ON restock_execution_attempts (store_id, proposal_id, started_at DESC, id);
