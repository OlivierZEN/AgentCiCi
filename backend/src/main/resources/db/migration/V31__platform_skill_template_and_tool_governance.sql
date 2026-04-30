CREATE TABLE IF NOT EXISTS platform_skill_template (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(64),
    description VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    current_version_no INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_platform_skill_template_org_code
    ON platform_skill_template(org_id, template_code);

CREATE TABLE IF NOT EXISTS platform_skill_template_version (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    version_no INTEGER NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    prompt_fragment TEXT,
    tool_whitelist TEXT,
    kb_whitelist TEXT,
    handoff_rule TEXT,
    output_contract TEXT,
    risk_level VARCHAR(32) NOT NULL,
    changelog TEXT,
    publish_status VARCHAR(32) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_platform_skill_template_version_org_code_version
    ON platform_skill_template_version(org_id, template_code, version_no);

CREATE INDEX IF NOT EXISTS idx_platform_skill_template_version_org_code_created
    ON platform_skill_template_version(org_id, template_code, created_at DESC);

CREATE TABLE IF NOT EXISTS platform_tool_definition (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    risk_level VARCHAR(32) NOT NULL,
    category VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_platform_tool_definition_org_tool
    ON platform_tool_definition(org_id, tool_name);
