CREATE TABLE IF NOT EXISTS customer_interaction_event (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL UNIQUE,
    org_id VARCHAR(64) NOT NULL,
    crm_account_id VARCHAR(128) NOT NULL,
    crm_contact_id VARCHAR(128),
    source_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    subject VARCHAR(256) NOT NULL,
    raw_summary TEXT NOT NULL,
    ai_summary TEXT NOT NULL,
    sentiment VARCHAR(32) NOT NULL,
    intent_tags TEXT NOT NULL,
    lifecycle_area VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_interaction_event_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_customer_interaction_event_org_account_time
    ON customer_interaction_event(org_id, crm_account_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS customer_workbench_recommendation (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL UNIQUE,
    org_id VARCHAR(64) NOT NULL,
    crm_account_id VARCHAR(128) NOT NULL,
    recommendation_type VARCHAR(48) NOT NULL,
    title VARCHAR(256) NOT NULL,
    rationale TEXT NOT NULL,
    confidence NUMERIC(5, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    crm_payload TEXT NOT NULL,
    applied_crm_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_workbench_recommendation_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_customer_workbench_recommendation_org_account_status
    ON customer_workbench_recommendation(org_id, crm_account_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS customer_workbench_snapshot (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL UNIQUE,
    org_id VARCHAR(64) NOT NULL,
    crm_account_id VARCHAR(128) NOT NULL,
    account_name VARCHAR(256) NOT NULL,
    owner_name VARCHAR(128) NOT NULL,
    segment VARCHAR(32) NOT NULL,
    health_score INTEGER NOT NULL,
    progress_score INTEGER NOT NULL,
    risk_count INTEGER NOT NULL,
    next_action_count INTEGER NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_workbench_snapshot_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_workbench_snapshot_org_account
    ON customer_workbench_snapshot(org_id, crm_account_id);

CREATE INDEX IF NOT EXISTS idx_customer_workbench_snapshot_org_updated
    ON customer_workbench_snapshot(org_id, updated_at DESC);
