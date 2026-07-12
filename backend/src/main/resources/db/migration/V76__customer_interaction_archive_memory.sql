ALTER TABLE customer_interaction_event
    ADD COLUMN IF NOT EXISTS source_batch_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS analysis_json TEXT NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS evidence_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS analysis_version INTEGER NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_customer_interaction_event_batch
    ON customer_interaction_event(org_id, source_batch_id);

UPDATE customer_interaction_event event
SET source_batch_id = batch.public_id,
    analysis_json = batch.analysis_json,
    evidence_count = (SELECT COUNT(*) FROM customer_interaction_asset asset WHERE asset.batch_id = batch.id),
    analysis_version = 1
FROM customer_interaction_batch batch
WHERE batch.confirmed_event_id = event.public_id
  AND (event.source_batch_id IS NULL OR event.source_batch_id = '');

CREATE TABLE IF NOT EXISTS customer_memory_item (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL UNIQUE,
    org_id VARCHAR(64) NOT NULL,
    crm_account_id VARCHAR(128) NOT NULL,
    source_event_id VARCHAR(64) NOT NULL,
    source_batch_id VARCHAR(64),
    memory_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    valid_until TIMESTAMP,
    evidence_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_memory_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_customer_memory_account_status_time
    ON customer_memory_item(org_id, crm_account_id, status, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_customer_memory_source_event
    ON customer_memory_item(org_id, source_event_id);
