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
    'sisi',
    '思思',
    '将 AgentCiCi 内部受治理智能体安全映射到 CloudCC CRM 等外部业务页面。',
    'ENABLED',
    'sdk_iframe',
    '/sdk/sisi.js',
    '/sdk/sisi@1.0.0.js',
    '/embed/sisi',
    '["chat:read","chat:write","attachment:write","voice:input"]',
    '["cloudcc"]',
    600,
    '{"defaultAgentId":"cici-system","modes":["page","float"],"postMessageEvents":["embed:ready","embed:resize","embed:conversation-started","embed:token-required","embed:action-confirmed","embed:error","embed:close"]}',
    '1.0.0',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM embed_app_definition WHERE app_code = 'sisi'
);

CREATE TABLE IF NOT EXISTS sisi_embed_session (
    id VARCHAR(64) PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL,
    app_code VARCHAR(64) NOT NULL,
    chat_session_id VARCHAR(64) NOT NULL,
    internal_user_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    external_tenant_id VARCHAR(128) NOT NULL,
    external_user_id VARCHAR(128) NOT NULL,
    source VARCHAR(32) NOT NULL,
    object_type VARCHAR(96) NOT NULL,
    object_id VARCHAR(160) NOT NULL,
    record_name VARCHAR(256),
    customer_name VARCHAR(256),
    parent_origin VARCHAR(256) NOT NULL,
    context_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_sisi_embed_session_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_sisi_embed_session_chat FOREIGN KEY (chat_session_id, company_id)
        REFERENCES chat_session(id, company_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sisi_embed_session_chat
    ON sisi_embed_session(chat_session_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sisi_embed_session_identity_context
    ON sisi_embed_session(company_id, app_code, agent_id, external_tenant_id, external_user_id, source, object_type, object_id);

CREATE INDEX IF NOT EXISTS idx_sisi_embed_session_company_updated
    ON sisi_embed_session(company_id, updated_at DESC);
