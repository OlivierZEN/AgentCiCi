ALTER TABLE customer_workbench_recommendation
    ADD COLUMN IF NOT EXISTS source_event_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source_batch_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS valid_until TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_customer_recommendation_action_key
    ON customer_workbench_recommendation(org_id, crm_account_id, action_key, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_customer_recommendation_source_event
    ON customer_workbench_recommendation(org_id, source_event_id);
