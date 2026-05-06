CREATE TABLE IF NOT EXISTS agent_run_trace (
    trace_id VARCHAR(64) PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary VARCHAR(512) NOT NULL,
    model_name VARCHAR(96),
    active_skill_code VARCHAR(128),
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP NOT NULL,
    elapsed_ms INTEGER NOT NULL,
    model_call_count INTEGER NOT NULL,
    tool_call_count INTEGER NOT NULL,
    rag_context_count INTEGER NOT NULL,
    knowledge_base_names_json TEXT NOT NULL,
    skill_names_json TEXT NOT NULL,
    nodes_json TEXT NOT NULL,
    detail_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_run_trace_visible
    ON agent_run_trace(org_id, user_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_run_trace_org_started
    ON agent_run_trace(org_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_run_trace_agent
    ON agent_run_trace(org_id, agent_id, started_at DESC);
