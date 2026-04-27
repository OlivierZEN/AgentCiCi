CREATE TABLE IF NOT EXISTS agent_definition (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    summary VARCHAR(512),
    greeting TEXT,
    model VARCHAR(128) NOT NULL,
    system_prompt TEXT,
    handoff_rule TEXT,
    safety_level VARCHAR(32) NOT NULL,
    execution_mode VARCHAR(32) NOT NULL,
    version_label VARCHAR(32),
    builtin BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    published_version_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_definition_org_agent
    ON agent_definition(org_id, agent_id);

CREATE INDEX IF NOT EXISTS idx_agent_definition_org_updated
    ON agent_definition(org_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS agent_spec (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    spec_text TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_spec_org_agent
    ON agent_spec(org_id, agent_id);

CREATE TABLE IF NOT EXISTS agent_workflow_version (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    version_no INTEGER NOT NULL,
    version_label VARCHAR(32),
    spec_text TEXT,
    workflow_code TEXT,
    workflow_manifest TEXT,
    workflow_preview TEXT,
    compile_summary TEXT,
    warnings TEXT,
    dependencies TEXT,
    publish_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_workflow_version_org_agent_no
    ON agent_workflow_version(org_id, agent_id, version_no);

CREATE INDEX IF NOT EXISTS idx_agent_workflow_version_org_agent_created
    ON agent_workflow_version(org_id, agent_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_kb_binding (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    priority INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_kb_binding_org_agent_kb
    ON agent_kb_binding(org_id, agent_id, knowledge_base_id);

CREATE INDEX IF NOT EXISTS idx_agent_kb_binding_org_agent_priority
    ON agent_kb_binding(org_id, agent_id, priority);

CREATE TABLE IF NOT EXISTS agent_tool_binding (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    tool_id VARCHAR(128) NOT NULL,
    priority INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_tool_binding_org_agent_tool
    ON agent_tool_binding(org_id, agent_id, tool_id);

CREATE INDEX IF NOT EXISTS idx_agent_tool_binding_org_agent_priority
    ON agent_tool_binding(org_id, agent_id, priority);

CREATE TABLE IF NOT EXISTS agent_channel_binding (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    channel_id VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_channel_binding_org_agent_channel
    ON agent_channel_binding(org_id, agent_id, channel_id);

CREATE TABLE IF NOT EXISTS agent_publish_config (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    channel_id VARCHAR(32) NOT NULL,
    config_json TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_publish_config_org_agent_channel
    ON agent_publish_config(org_id, agent_id, channel_id);
