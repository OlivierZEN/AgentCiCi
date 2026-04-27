ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS agent_id VARCHAR(64);

CREATE TABLE IF NOT EXISTS skill_definition (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    skill_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    builtin BOOLEAN NOT NULL,
    enabled BOOLEAN NOT NULL,
    prompt_fragment TEXT,
    tool_whitelist TEXT,
    kb_whitelist TEXT,
    handoff_rule TEXT,
    output_contract TEXT,
    risk_level VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_skill_binding (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    skill_id BIGINT NOT NULL REFERENCES skill_definition(id),
    activation_mode VARCHAR(32) NOT NULL,
    activation_condition TEXT,
    priority INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_skill_definition_org_code
    ON skill_definition(org_id, skill_code);

CREATE INDEX IF NOT EXISTS idx_skill_definition_org_enabled
    ON skill_definition(org_id, enabled);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_skill_binding_org_agent_skill
    ON agent_skill_binding(org_id, agent_id, skill_id);

CREATE INDEX IF NOT EXISTS idx_agent_skill_binding_org_agent
    ON agent_skill_binding(org_id, agent_id, enabled, priority);

CREATE INDEX IF NOT EXISTS idx_chat_session_org_agent
    ON chat_session(org_id, agent_id);
