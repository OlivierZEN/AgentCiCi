CREATE TABLE IF NOT EXISTS agent_task_run (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(128),
    agent_id VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    mode VARCHAR(24) NOT NULL,
    status VARCHAR(32) NOT NULL,
    goal_summary VARCHAR(512) NOT NULL,
    max_steps INTEGER NOT NULL,
    current_plan_id BIGINT,
    lease_owner VARCHAR(128),
    lease_expires_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_agent_task_run_org_session_created
    ON agent_task_run(org_id, session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_task_run_org_agent_status_created
    ON agent_task_run(org_id, agent_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_task_run_recovery
    ON agent_task_run(status, lease_expires_at);

CREATE TABLE IF NOT EXISTS agent_task_plan (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    run_id BIGINT NOT NULL REFERENCES agent_task_run(id) ON DELETE CASCADE,
    revision_no INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    goal_summary VARCHAR(512) NOT NULL,
    plan_json TEXT NOT NULL,
    plan_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (org_id, run_id, revision_no)
);

CREATE INDEX IF NOT EXISTS idx_agent_task_plan_org_run_revision
    ON agent_task_plan(org_id, run_id, revision_no DESC);

CREATE TABLE IF NOT EXISTS agent_task_step (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    run_id BIGINT NOT NULL REFERENCES agent_task_run(id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES agent_task_plan(id) ON DELETE CASCADE,
    step_key VARCHAR(64) NOT NULL,
    step_order INTEGER NOT NULL,
    step_kind VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    depends_on_json TEXT NOT NULL,
    allowed_tool_names_json TEXT NOT NULL,
    expected_evidence_json TEXT NOT NULL,
    attempt_no INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(64),
    result_summary VARCHAR(1024),
    lease_owner VARCHAR(128),
    lease_expires_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (org_id, plan_id, step_key)
);

CREATE INDEX IF NOT EXISTS idx_agent_task_step_org_run_status_order
    ON agent_task_step(org_id, run_id, status, step_order);
CREATE INDEX IF NOT EXISTS idx_agent_task_step_recovery
    ON agent_task_step(status, lease_expires_at);

CREATE TABLE IF NOT EXISTS agent_task_event (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    run_id BIGINT NOT NULL REFERENCES agent_task_run(id) ON DELETE CASCADE,
    step_id BIGINT REFERENCES agent_task_step(id) ON DELETE CASCADE,
    event_type VARCHAR(48) NOT NULL,
    payload_redacted_json TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_task_event_org_run_occurred
    ON agent_task_event(org_id, run_id, occurred_at, id);
