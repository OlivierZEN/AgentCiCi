ALTER TABLE agent_api_session_map ADD COLUMN IF NOT EXISTS conversation_name VARCHAR(160);
ALTER TABLE agent_api_session_map ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS agent_api_task (
    task_id VARCHAR(64) PRIMARY KEY,
    request_id VARCHAR(64),
    org_id VARCHAR(64) NOT NULL,
    credential_id BIGINT NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    external_user_id VARCHAR(128),
    external_session_id VARCHAR(160),
    status VARCHAR(32) NOT NULL,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_api_task_credential_created
    ON agent_api_task(credential_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_api_message (
    message_id VARCHAR(64) PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    org_id VARCHAR(64) NOT NULL,
    credential_id BIGINT NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    external_user_id VARCHAR(128),
    external_session_id VARCHAR(160),
    internal_session_id VARCHAR(64) NOT NULL,
    query TEXT,
    answer TEXT,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(64),
    idempotency_key VARCHAR(128),
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_api_message_conversation
    ON agent_api_message(org_id, credential_id, agent_id, external_session_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_api_message_task
    ON agent_api_message(task_id);

CREATE INDEX IF NOT EXISTS idx_agent_api_message_idempotency
    ON agent_api_message(credential_id, idempotency_key);

CREATE TABLE IF NOT EXISTS agent_api_feedback (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    org_id VARCHAR(64) NOT NULL,
    credential_id BIGINT NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    rating VARCHAR(32) NOT NULL,
    content TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_api_feedback_message
    ON agent_api_feedback(message_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_api_file (
    file_id VARCHAR(64) PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    credential_id BIGINT NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    external_user_id VARCHAR(128),
    external_session_id VARCHAR(160),
    name VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(128),
    storage_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_api_file_owner
    ON agent_api_file(org_id, credential_id, agent_id, external_user_id, created_at DESC);
