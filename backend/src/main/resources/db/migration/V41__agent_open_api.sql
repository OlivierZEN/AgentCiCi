CREATE TABLE IF NOT EXISTS agent_api_credential (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(32) NOT NULL,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    key_prefix VARCHAR(64) NOT NULL,
    key_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    run_as_user_id VARCHAR(64) NOT NULL,
    allowed_ips_json TEXT NOT NULL,
    scopes_json TEXT NOT NULL,
    rate_limit_per_minute INTEGER NOT NULL,
    daily_quota INTEGER NOT NULL,
    max_prompt_chars INTEGER NOT NULL,
    max_response_chars INTEGER NOT NULL,
    allow_stream BOOLEAN NOT NULL DEFAULT TRUE,
    allow_trace_read BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    created_by VARCHAR(64) NOT NULL,
    revoked_by VARCHAR(64),
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_api_credential_public_id
    ON agent_api_credential(public_id);

CREATE INDEX IF NOT EXISTS idx_agent_api_credential_org_agent
    ON agent_api_credential(org_id, agent_id, status);

CREATE TABLE IF NOT EXISTS agent_api_session_map (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    credential_id BIGINT NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    external_session_id VARCHAR(160) NOT NULL,
    internal_session_id VARCHAR(64) NOT NULL,
    external_user_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_api_session_external
    ON agent_api_session_map(org_id, credential_id, agent_id, external_session_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_api_session_internal
    ON agent_api_session_map(internal_session_id);

CREATE TABLE IF NOT EXISTS agent_api_call_log (
    request_id VARCHAR(64) PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    credential_id BIGINT NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    run_as_user_id VARCHAR(64) NOT NULL,
    external_session_id VARCHAR(160),
    internal_session_id VARCHAR(64) NOT NULL,
    external_user_id VARCHAR(128),
    client_ip VARCHAR(64),
    idempotency_key VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    http_status INTEGER NOT NULL,
    error_code VARCHAR(64),
    trace_id VARCHAR(64),
    prompt_chars INTEGER NOT NULL DEFAULT 0,
    response_chars INTEGER NOT NULL DEFAULT 0,
    elapsed_ms INTEGER NOT NULL DEFAULT 0,
    request_summary TEXT,
    response_summary TEXT,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_api_call_log_org_agent_created
    ON agent_api_call_log(org_id, agent_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_api_call_log_credential_created
    ON agent_api_call_log(credential_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_api_usage_daily (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    credential_id BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    call_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    total_elapsed_ms BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_api_usage_daily
    ON agent_api_usage_daily(org_id, credential_id, usage_date);

ALTER TABLE agent_run_trace ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) DEFAULT 'internal';
ALTER TABLE agent_run_trace ADD COLUMN IF NOT EXISTS request_id VARCHAR(64);
ALTER TABLE agent_run_trace ADD COLUMN IF NOT EXISTS credential_id BIGINT;
ALTER TABLE agent_run_trace ADD COLUMN IF NOT EXISTS external_user_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_agent_run_trace_api
    ON agent_run_trace(org_id, credential_id, started_at DESC);
