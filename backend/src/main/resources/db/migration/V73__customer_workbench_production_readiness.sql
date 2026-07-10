ALTER TABLE customer_workbench_recommendation
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS target_object VARCHAR(64),
    ADD COLUMN IF NOT EXISTS target_record_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS evidence_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS dismissal_reason TEXT,
    ADD COLUMN IF NOT EXISTS confirmed_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS applied_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(128),
    ADD COLUMN IF NOT EXISTS last_error_message TEXT;

CREATE TABLE IF NOT EXISTS customer_signal (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL UNIQUE,
    org_id VARCHAR(64) NOT NULL,
    crm_account_id VARCHAR(128) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    signal_type VARCHAR(64) NOT NULL,
    title VARCHAR(256) NOT NULL,
    detail TEXT NOT NULL,
    severity VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    evidence_json TEXT NOT NULL,
    assignee VARCHAR(128),
    source_updated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_signal_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_signal_org_public
    ON customer_signal(org_id, public_id);
CREATE INDEX IF NOT EXISTS idx_customer_signal_org_account_status
    ON customer_signal(org_id, crm_account_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS customer_follow_subscription (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    crm_account_id VARCHAR(128) NOT NULL,
    notification_policy VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_follow_subscription_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_follow_org_user_account
    ON customer_follow_subscription(org_id, user_id, crm_account_id);

CREATE TABLE IF NOT EXISTS customer_crm_write_audit (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL UNIQUE,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    recommendation_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    target_object VARCHAR(64) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    remote_record_id VARCHAR(128),
    error_code VARCHAR(128),
    error_message TEXT,
    request_summary TEXT NOT NULL,
    response_summary TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_crm_write_audit_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_crm_write_audit_idempotency
    ON customer_crm_write_audit(org_id, user_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_customer_crm_write_audit_recommendation
    ON customer_crm_write_audit(org_id, recommendation_id, created_at DESC);
