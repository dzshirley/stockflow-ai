ALTER TABLE restock_proposals ADD COLUMN model_input_tokens INTEGER;
ALTER TABLE restock_proposals ADD COLUMN model_output_tokens INTEGER;
ALTER TABLE restock_proposals ADD COLUMN model_total_tokens INTEGER;
ALTER TABLE restock_proposals ADD COLUMN model_estimated_cost_usd NUMERIC(19,8);
ALTER TABLE restock_proposals ADD COLUMN model_pricing_version VARCHAR(40);
ALTER TABLE restock_proposals ADD COLUMN model_latency_ms BIGINT;

ALTER TABLE restock_proposals ADD CONSTRAINT ck_restock_model_usage CHECK (
    (model_input_tokens IS NULL AND model_output_tokens IS NULL AND model_total_tokens IS NULL)
    OR (model_input_tokens >= 0 AND model_output_tokens >= 0
        AND model_total_tokens >= model_input_tokens + model_output_tokens));
ALTER TABLE restock_proposals ADD CONSTRAINT ck_restock_model_cost CHECK (
    model_estimated_cost_usd IS NULL OR model_estimated_cost_usd >= 0);
ALTER TABLE restock_proposals ADD CONSTRAINT ck_restock_model_latency CHECK (
    model_latency_ms IS NULL OR model_latency_ms >= 0);
