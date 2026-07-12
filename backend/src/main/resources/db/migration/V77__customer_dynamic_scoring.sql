CREATE TABLE IF NOT EXISTS customer_dynamic_signal (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL UNIQUE,
    org_id VARCHAR(64) NOT NULL,
    crm_account_id VARCHAR(128) NOT NULL,
    source_event_id VARCHAR(64) NOT NULL,
    source_batch_id VARCHAR(64),
    source_type VARCHAR(32) NOT NULL,
    dimension VARCHAR(32) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    impact INTEGER NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    title VARCHAR(256) NOT NULL,
    rationale TEXT NOT NULL,
    evidence_quote TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    valid_until TIMESTAMP,
    content_fingerprint VARCHAR(64) NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_dynamic_signal_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_customer_dynamic_signal_account_status
    ON customer_dynamic_signal(org_id, crm_account_id, status, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_customer_dynamic_signal_source
    ON customer_dynamic_signal(org_id, source_event_id);

CREATE TABLE IF NOT EXISTS customer_score_snapshot (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    crm_account_id VARCHAR(128) NOT NULL,
    health_score INTEGER NOT NULL,
    health_dimension_score INTEGER NOT NULL,
    expansion_score INTEGER NOT NULL,
    renewal_score INTEGER NOT NULL,
    relationship_score INTEGER NOT NULL,
    risk_score INTEGER NOT NULL,
    net_change_30d DOUBLE PRECISION NOT NULL,
    active_signal_count INTEGER NOT NULL,
    pending_signal_count INTEGER NOT NULL,
    calculation_version VARCHAR(64) NOT NULL,
    calculated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_customer_score_snapshot_account UNIQUE (org_id, crm_account_id),
    CONSTRAINT fk_customer_score_snapshot_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_customer_score_snapshot_org
    ON customer_score_snapshot(org_id, calculated_at DESC);
