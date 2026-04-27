CREATE TABLE IF NOT EXISTS chat_session (
    id VARCHAR(64) PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    org_id VARCHAR(64) NOT NULL,
    role_code VARCHAR(16) NOT NULL,
    content VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS org_model_config (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    scene_code VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model_name VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS tool_definition (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    description VARCHAR(256) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS kb_chunk (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id VARCHAR(64) NOT NULL,
    content VARCHAR(4000) NOT NULL,
    tags VARCHAR(256)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    detail VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_session_org_user ON chat_session(org_id, user_id);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_org_kb ON kb_chunk(org_id, knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_org ON audit_log(org_id);
