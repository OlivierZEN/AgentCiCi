ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS runtime_api_draft_json TEXT;

ALTER TABLE skill_version
    ADD COLUMN IF NOT EXISTS runtime_api_snapshot_json TEXT;

CREATE TABLE IF NOT EXISTS skill_api_tool (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    skill_id BIGINT NOT NULL,
    skill_version_id BIGINT NOT NULL,
    skill_code VARCHAR(64) NOT NULL,
    api_code VARCHAR(64) NOT NULL,
    tool_name VARCHAR(160) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    trigger_mode VARCHAR(32) NOT NULL DEFAULT 'model_decide',
    input_schema_json TEXT NOT NULL,
    execution_plan_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_skill_api_tool_version_api
    ON skill_api_tool(org_id, skill_version_id, api_code);

CREATE UNIQUE INDEX IF NOT EXISTS ux_skill_api_tool_tool_name
    ON skill_api_tool(org_id, tool_name);

CREATE INDEX IF NOT EXISTS idx_skill_api_tool_runtime
    ON skill_api_tool(org_id, skill_version_id, enabled);
