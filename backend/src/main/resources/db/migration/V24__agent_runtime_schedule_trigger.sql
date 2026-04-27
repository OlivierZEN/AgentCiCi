CREATE TABLE IF NOT EXISTS agent_runtime_schedule_trigger (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    workflow_version_id BIGINT,
    version_no INTEGER,
    trigger_key VARCHAR(128) NOT NULL,
    title VARCHAR(256) NOT NULL,
    cadence VARCHAR(64),
    detail VARCHAR(1024),
    source VARCHAR(32) NOT NULL,
    stub BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_runtime_sched_org_agent_active
    ON agent_runtime_schedule_trigger(org_id, agent_id, active, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_agent_runtime_sched_org_agent_key_active
    ON agent_runtime_schedule_trigger(org_id, agent_id, trigger_key, active);
