DROP INDEX CONCURRENTLY IF EXISTS idx_agent_workflow_skill_ref_org_skill_impact;

CREATE INDEX CONCURRENTLY idx_agent_workflow_skill_ref_org_skill_impact
    ON agent_workflow_skill_ref(org_id, skill_id, skill_version_id, workflow_version_id);

DROP INDEX CONCURRENTLY IF EXISTS idx_agent_skill_binding_org_skill_impact;

CREATE INDEX CONCURRENTLY idx_agent_skill_binding_org_skill_impact
    ON agent_skill_binding(org_id, skill_id, enabled, agent_id, priority);
