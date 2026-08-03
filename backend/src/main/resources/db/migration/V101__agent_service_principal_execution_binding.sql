CREATE TABLE agent_service_principal_binding (
    company_id VARCHAR(64) NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    agent_id VARCHAR(64) NOT NULL,
    service_principal_id VARCHAR(64) NOT NULL REFERENCES service_principal(principal_id),
    delegation_policy VARCHAR(32) NOT NULL DEFAULT 'PRIMARY_OWNER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    configured_by_principal_id VARCHAR(64) NOT NULL REFERENCES principal(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (company_id, agent_id),
    CONSTRAINT chk_agent_service_principal_delegation
        CHECK (delegation_policy IN ('PRIMARY_OWNER'))
);

CREATE INDEX idx_agent_service_principal_binding_principal
    ON agent_service_principal_binding(service_principal_id, enabled);

-- Migrate the two native capabilities from the former runtime-only injection into
-- explicit Agent Builder bindings. The runtime no longer grants them by agent id.
INSERT INTO agent_tool_binding (
    company_id, agent_id, tool_id, priority, enabled, created_at
)
SELECT company_id, agent_id, 'semattice_project_delivery_query', 10, TRUE, CURRENT_TIMESTAMP
FROM agent_definition
WHERE agent_id = 'dev-autopilot-pm'
ON CONFLICT (company_id, agent_id, tool_id) DO UPDATE
SET enabled = TRUE,
    priority = EXCLUDED.priority;

INSERT INTO agent_tool_binding (
    company_id, agent_id, tool_id, priority, enabled, created_at
)
SELECT company_id, agent_id, 'semattice_project_delivery_create', 20, TRUE, CURRENT_TIMESTAMP
FROM agent_definition
WHERE agent_id = 'dev-autopilot-pm'
ON CONFLICT (company_id, agent_id, tool_id) DO UPDATE
SET enabled = TRUE,
    priority = EXCLUDED.priority;

-- Bind the existing managed DEV Autopilot product-manager identity when it and
-- its accountable PRIMARY owner are already present in the same company.
INSERT INTO agent_service_principal_binding (
    company_id,
    agent_id,
    service_principal_id,
    delegation_policy,
    enabled,
    configured_by_principal_id,
    created_at,
    updated_at
)
SELECT agent.company_id,
       agent.agent_id,
       service.principal_id,
       'PRIMARY_OWNER',
       TRUE,
       owner.owner_principal_id,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM agent_definition agent
JOIN service_principal service
  ON service.client_id = 'dev-autopilot-product-manager'
JOIN service_principal_owner owner
  ON owner.service_principal_id = service.principal_id
 AND owner.owner_role = 'PRIMARY'
 AND owner.owner_status = 'ACTIVE'
JOIN company_member member
  ON member.id = owner.company_member_id
 AND member.account_id = owner.owner_principal_id
 AND member.company_id = agent.company_id
WHERE agent.agent_id = 'dev-autopilot-pm'
ON CONFLICT (company_id, agent_id) DO UPDATE
SET service_principal_id = EXCLUDED.service_principal_id,
    delegation_policy = EXCLUDED.delegation_policy,
    enabled = TRUE,
    configured_by_principal_id = EXCLUDED.configured_by_principal_id,
    updated_at = EXCLUDED.updated_at;
