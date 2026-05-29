CREATE TABLE IF NOT EXISTS billing_edition (
    id BIGSERIAL PRIMARY KEY,
    deployment_mode VARCHAR(32) NOT NULL,
    edition_code VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    operation_seat_limit INTEGER,
    builder_seat_limit INTEGER,
    agent_limit INTEGER,
    skill_limit INTEGER,
    workflow_limit INTEGER,
    knowledge_base_limit INTEGER,
    document_limit INTEGER,
    chunk_limit INTEGER,
    knowledge_storage_mb INTEGER,
    open_api_qps INTEGER,
    open_api_concurrency INTEGER,
    open_api_credential_limit INTEGER,
    connector_limit INTEGER,
    meeting_minutes_concurrency INTEGER,
    trace_retention_days INTEGER,
    audit_retention_days INTEGER,
    environment_limit INTEGER,
    included_credits NUMERIC(18, 2) NOT NULL DEFAULT 0,
    overage_mode VARCHAR(32) NOT NULL DEFAULT 'soft_limit',
    billing_type_policy VARCHAR(32) NOT NULL DEFAULT 'included',
    sla_tier_code VARCHAR(64) NOT NULL DEFAULT 'standard',
    top_up_policy VARCHAR(64) NOT NULL DEFAULT 'disabled',
    local_model_token_policy VARCHAR(1000) NOT NULL DEFAULT '',
    platform_paid_resource_policy VARCHAR(1000) NOT NULL DEFAULT '',
    package_codes TEXT NOT NULL DEFAULT '[]',
    version_no INTEGER NOT NULL DEFAULT 1,
    change_reason VARCHAR(1000) NOT NULL DEFAULT 'initial seed',
    updated_by VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_billing_edition_deployment_mode ON billing_edition (deployment_mode, sort_order);

CREATE TABLE IF NOT EXISTS billing_package (
    id BIGSERIAL PRIMARY KEY,
    deployment_mode VARCHAR(32) NOT NULL,
    package_code VARCHAR(64) NOT NULL UNIQUE,
    package_type VARCHAR(32) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    config_json TEXT NOT NULL DEFAULT '{}',
    version_no INTEGER NOT NULL DEFAULT 1,
    change_reason VARCHAR(1000) NOT NULL DEFAULT 'initial seed',
    updated_by VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_billing_package_mode_type ON billing_package (deployment_mode, package_type, sort_order);

CREATE TABLE IF NOT EXISTS billing_config_change_log (
    id BIGSERIAL PRIMARY KEY,
    config_type VARCHAR(32) NOT NULL,
    config_code VARCHAR(64) NOT NULL,
    version_no INTEGER NOT NULL,
    high_risk BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(1000) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_billing_config_change_log_resource
    ON billing_config_change_log (config_type, config_code, version_no DESC);

CREATE TABLE IF NOT EXISTS billing_subscription (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL UNIQUE,
    deployment_mode VARCHAR(32) NOT NULL,
    edition_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    included_credits NUMERIC(18, 2) NOT NULL DEFAULT 0,
    consumed_credits NUMERIC(18, 2) NOT NULL DEFAULT 0,
    remaining_credits NUMERIC(18, 2) NOT NULL DEFAULT 0,
    operation_seats_used INTEGER NOT NULL DEFAULT 0,
    builder_seats_used INTEGER NOT NULL DEFAULT 0,
    package_codes TEXT NOT NULL DEFAULT '[]',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_billing_subscription_edition
    ON billing_subscription (deployment_mode, edition_code);

CREATE TABLE IF NOT EXISTS usage_meter_event (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    agent_id VARCHAR(64),
    billable_domain VARCHAR(64) NOT NULL,
    billable_item_code VARCHAR(128) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    quantity NUMERIC(18, 4) NOT NULL DEFAULT 1,
    unit VARCHAR(32) NOT NULL DEFAULT 'run',
    work_credit_quantity NUMERIC(18, 2) NOT NULL DEFAULT 0,
    billing_type VARCHAR(32) NOT NULL DEFAULT 'included',
    source_type VARCHAR(64) NOT NULL DEFAULT 'manual',
    source_id VARCHAR(128) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL DEFAULT 'succeeded',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata_json TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_usage_meter_event_org_time
    ON usage_meter_event (org_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_usage_meter_event_org_domain
    ON usage_meter_event (org_id, billable_domain, occurred_at DESC);

CREATE TABLE IF NOT EXISTS billing_credit_ledger (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    entry_type VARCHAR(64) NOT NULL,
    credits_delta NUMERIC(18, 2) NOT NULL,
    balance_after NUMERIC(18, 2) NOT NULL,
    source_event_id BIGINT,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata_json TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_billing_credit_ledger_org_time
    ON billing_credit_ledger (org_id, occurred_at DESC);
