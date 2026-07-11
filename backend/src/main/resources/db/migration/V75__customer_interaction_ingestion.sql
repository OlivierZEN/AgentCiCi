CREATE TABLE IF NOT EXISTS customer_interaction_batch (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL UNIQUE,
    org_id VARCHAR(64) NOT NULL,
    crm_account_id VARCHAR(128) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    subject VARCHAR(256) NOT NULL,
    narration_text TEXT NOT NULL,
    pasted_text TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    combined_text TEXT NOT NULL,
    analysis_json TEXT NOT NULL,
    error_message VARCHAR(1000) NOT NULL,
    confirmed_event_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_interaction_batch_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_customer_interaction_batch_account_time
    ON customer_interaction_batch(org_id, crm_account_id, created_at DESC);

CREATE TABLE IF NOT EXISTS customer_interaction_asset (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL UNIQUE,
    batch_id BIGINT NOT NULL,
    org_id VARCHAR(64) NOT NULL,
    input_type VARCHAR(32) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_path VARCHAR(768) NOT NULL,
    sort_order INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    extracted_text TEXT NOT NULL,
    error_message VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_interaction_asset_batch FOREIGN KEY (batch_id) REFERENCES customer_interaction_batch(id),
    CONSTRAINT fk_customer_interaction_asset_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_customer_interaction_asset_batch_order
    ON customer_interaction_asset(batch_id, sort_order ASC);

CREATE INDEX IF NOT EXISTS idx_customer_interaction_asset_dedup
    ON customer_interaction_asset(org_id, sha256);

