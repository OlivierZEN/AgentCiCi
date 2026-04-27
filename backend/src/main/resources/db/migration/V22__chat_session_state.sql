CREATE TABLE IF NOT EXISTS chat_session_state (
    session_id VARCHAR(64) PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64),
    summary VARCHAR(512) NOT NULL,
    state_json TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_session_state_org_updated
    ON chat_session_state(org_id, updated_at);
