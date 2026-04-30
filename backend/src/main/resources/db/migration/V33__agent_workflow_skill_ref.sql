CREATE TABLE IF NOT EXISTS agent_workflow_skill_ref (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    workflow_version_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    skill_version_id BIGINT,
    template_code VARCHAR(64),
    template_version_no INTEGER,
    reference_mode VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_agent_workflow_skill_ref_org_workflow_skill
    ON agent_workflow_skill_ref(org_id, workflow_version_id, skill_id);

CREATE INDEX IF NOT EXISTS idx_agent_workflow_skill_ref_org_workflow
    ON agent_workflow_skill_ref(org_id, workflow_version_id, id);

CREATE INDEX IF NOT EXISTS idx_agent_workflow_skill_ref_org_skill_version
    ON agent_workflow_skill_ref(org_id, skill_version_id);
