CREATE TABLE IF NOT EXISTS user_agent_profile (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    locale VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
    notification_target_json TEXT NOT NULL DEFAULT '{}',
    personal_context_json TEXT NOT NULL DEFAULT '{}',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_agent_profile_org_user_agent
    ON user_agent_profile(org_id, user_id, agent_id);

CREATE TABLE IF NOT EXISTS user_workflow_spec (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    source_text TEXT NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    draft_version_no INTEGER,
    published_version_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_workflow_spec_org_user_agent
    ON user_workflow_spec(org_id, user_id, agent_id);

CREATE TABLE IF NOT EXISTS user_workflow_version (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    spec_id BIGINT NOT NULL,
    version_no INTEGER NOT NULL,
    version_label VARCHAR(64),
    spec_text TEXT NOT NULL,
    workflow_code TEXT NOT NULL,
    workflow_manifest TEXT NOT NULL,
    workflow_preview TEXT NOT NULL,
    compile_summary TEXT NOT NULL,
    warnings TEXT NOT NULL,
    dependencies TEXT NOT NULL,
    publish_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_workflow_version_org_user_agent_version
    ON user_workflow_version(org_id, user_id, agent_id, version_no);

CREATE INDEX IF NOT EXISTS idx_user_workflow_version_org_user_agent
    ON user_workflow_version(org_id, user_id, agent_id);

CREATE TABLE IF NOT EXISTS user_workflow_trigger (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    version_id BIGINT NOT NULL,
    routine_key VARCHAR(128) NOT NULL,
    routine_name VARCHAR(256) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    cron_expr VARCHAR(64),
    timezone VARCHAR(64),
    interval_seconds INTEGER,
    event_type VARCHAR(64),
    event_filter_json TEXT NOT NULL DEFAULT '{}',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    next_fire_at TIMESTAMP,
    last_triggered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_workflow_trigger_due
    ON user_workflow_trigger(enabled, next_fire_at);

CREATE INDEX IF NOT EXISTS idx_user_workflow_trigger_org_user_agent
    ON user_workflow_trigger(org_id, user_id, agent_id);

CREATE TABLE IF NOT EXISTS user_workflow_execution (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    version_id BIGINT NOT NULL,
    trigger_id BIGINT,
    routine_key VARCHAR(128) NOT NULL,
    trigger_source VARCHAR(32) NOT NULL,
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    trace_json TEXT NOT NULL DEFAULT '[]',
    output_summary TEXT,
    error_code VARCHAR(64),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_workflow_execution_org_user_agent
    ON user_workflow_execution(org_id, user_id, agent_id, created_at DESC);
