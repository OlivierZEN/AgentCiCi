ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS draft_spec_text TEXT;

CREATE TABLE IF NOT EXISTS skill_version (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    skill_id BIGINT NOT NULL REFERENCES skill_definition(id),
    version_no INTEGER NOT NULL,
    spec_text TEXT,
    skill_kind VARCHAR(32) NOT NULL,
    compiled_prompt_fragment TEXT,
    compiled_policy_json TEXT,
    effective_tool_whitelist TEXT,
    effective_kb_whitelist TEXT,
    risk_level VARCHAR(32) NOT NULL,
    compile_summary TEXT,
    warnings TEXT,
    publish_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_skill_version_org_skill_version
    ON skill_version(org_id, skill_id, version_no);

CREATE INDEX IF NOT EXISTS idx_skill_version_org_skill_created
    ON skill_version(org_id, skill_id, created_at DESC);
