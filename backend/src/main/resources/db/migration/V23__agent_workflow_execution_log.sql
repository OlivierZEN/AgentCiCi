CREATE TABLE IF NOT EXISTS agent_workflow_execution_log (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    workflow_version_id BIGINT,
    version_no INTEGER,
    source VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    duration_ms INTEGER NOT NULL DEFAULT 0,
    summary VARCHAR(1024) NOT NULL,
    error_hint VARCHAR(512),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_exec_log_org_agent_created
    ON agent_workflow_execution_log(org_id, agent_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_exec_log_org_agent_version
    ON agent_workflow_execution_log(org_id, agent_id, version_no DESC, created_at DESC);
