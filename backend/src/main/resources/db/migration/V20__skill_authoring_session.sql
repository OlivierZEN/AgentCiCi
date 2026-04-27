CREATE TABLE IF NOT EXISTS skill_authoring_session (
    id VARCHAR(36) PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    state_json TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_skill_authoring_session_org_status_updated
    ON skill_authoring_session (org_id, status, updated_at DESC);
