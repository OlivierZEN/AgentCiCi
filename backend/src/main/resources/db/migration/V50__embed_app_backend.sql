CREATE TABLE IF NOT EXISTS embed_app_definition (
    app_code VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    embed_mode VARCHAR(32) NOT NULL,
    stable_sdk_url VARCHAR(256) NOT NULL,
    versioned_sdk_url VARCHAR(256) NOT NULL,
    embed_url VARCHAR(256) NOT NULL,
    required_scopes_json TEXT NOT NULL,
    supported_sources_json TEXT NOT NULL,
    default_token_ttl_seconds INTEGER NOT NULL,
    doc_json TEXT NOT NULL,
    version VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO embed_app_definition (
    app_code,
    name,
    description,
    status,
    embed_mode,
    stable_sdk_url,
    versioned_sdk_url,
    embed_url,
    required_scopes_json,
    supported_sources_json,
    default_token_ttl_seconds,
    doc_json,
    version,
    created_at,
    updated_at
)
SELECT
    'meeting-minutes',
    '会议纪要',
    '在 CRM 记录页嵌入实时会议听记、AI 纪要和写回候选。',
    'ENABLED',
    'sdk_iframe',
    '/sdk/meeting-minutes.js',
    '/sdk/meeting-minutes@1.0.0.js',
    '/embed/meeting-minutes',
    '["meeting:start","meeting:summary","crm:writeback"]',
    '["cloudcc","salesforce","custom"]',
    900,
    '{"cloudccVueExample":"window.AgentCiCiMeeting.open({ token, mode: ''drawer'', context })","postMessageEvents":["embed:ready","embed:meeting-started","embed:summary-generated","embed:writeback-preview","embed:error"]}',
    '1.0.0',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM embed_app_definition WHERE app_code = 'meeting-minutes'
);

CREATE TABLE IF NOT EXISTS org_embed_app_config (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    app_code VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    allowed_origins_json TEXT NOT NULL,
    run_as_user_id VARCHAR(64),
    source_bindings_json TEXT NOT NULL,
    scope_overrides_json TEXT NOT NULL,
    token_ttl_seconds INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_org_embed_app_config_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_org_embed_app_config_definition FOREIGN KEY (app_code) REFERENCES embed_app_definition(app_code)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_org_embed_app_config
    ON org_embed_app_config(org_id, app_code);

CREATE TABLE IF NOT EXISTS meeting_session (
    id VARCHAR(64) PRIMARY KEY,
    token_nonce VARCHAR(128) NOT NULL,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    external_user_id VARCHAR(128),
    source VARCHAR(32) NOT NULL,
    app_code VARCHAR(64) NOT NULL,
    object_type VARCHAR(96) NOT NULL,
    object_id VARCHAR(160) NOT NULL,
    record_name VARCHAR(256),
    customer_name VARCHAR(256),
    parent_origin VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL,
    context_json TEXT NOT NULL,
    summary_markdown TEXT,
    writeback_preview_json TEXT,
    writeback_result_json TEXT,
    trace_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_meeting_session_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_meeting_session_token_nonce
    ON meeting_session(token_nonce);

CREATE INDEX IF NOT EXISTS idx_meeting_session_org_object
    ON meeting_session(org_id, app_code, source, object_type, object_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_meeting_session_org_updated
    ON meeting_session(org_id, updated_at DESC);

ALTER TABLE agent_run_trace ADD COLUMN IF NOT EXISTS embed_app_code VARCHAR(64);
ALTER TABLE agent_run_trace ADD COLUMN IF NOT EXISTS meeting_session_id VARCHAR(64);
ALTER TABLE agent_run_trace ADD COLUMN IF NOT EXISTS source_object_type VARCHAR(96);
ALTER TABLE agent_run_trace ADD COLUMN IF NOT EXISTS source_object_id VARCHAR(160);

CREATE INDEX IF NOT EXISTS idx_agent_run_trace_embed
    ON agent_run_trace(org_id, embed_app_code, meeting_session_id, started_at DESC);
