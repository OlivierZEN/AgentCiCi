ALTER TABLE agent_definition
    ADD COLUMN IF NOT EXISTS owner_user_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_agent_definition_owner
    ON agent_definition(org_id, owner_user_id);

CREATE TABLE IF NOT EXISTS agent_access_grant (
    id VARCHAR(64) PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    principal_type VARCHAR(32) NOT NULL,
    principal_id VARCHAR(128),
    permission VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    granted_by VARCHAR(64),
    expires_at TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_agent_access_principal_type CHECK (principal_type IN ('ORG', 'USER', 'SYSTEM_ROLE', 'GROUP', 'CUSTOM_ROLE', 'DEPARTMENT')),
    CONSTRAINT ck_agent_access_permission CHECK (permission IN ('VIEW', 'RUN', 'DEBUG', 'EDIT', 'PUBLISH', 'MANAGE', 'OPENAPI', 'LOG_VIEW'))
);

CREATE INDEX IF NOT EXISTS idx_agent_access_grant_principal
    ON agent_access_grant(org_id, agent_id, principal_type, principal_id, permission, status);

CREATE INDEX IF NOT EXISTS idx_agent_access_grant_lookup
    ON agent_access_grant(org_id, agent_id, status, permission);

CREATE TABLE IF NOT EXISTS agent_permission_audit (
    id VARCHAR(64) PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(64),
    action VARCHAR(32) NOT NULL,
    target_principal_type VARCHAR(32),
    target_principal_id VARCHAR(128),
    permission VARCHAR(32),
    before_json TEXT,
    after_json TEXT,
    reason VARCHAR(512),
    trace_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_permission_audit_org_agent_created
    ON agent_permission_audit(org_id, agent_id, created_at DESC);

INSERT INTO agent_access_grant (
    id,
    org_id,
    agent_id,
    principal_type,
    principal_id,
    permission,
    source,
    granted_by,
    expires_at,
    status,
    created_at,
    updated_at
)
SELECT
    'seed-' || CAST(ROW_NUMBER() OVER () AS VARCHAR),
    d.org_id,
    d.agent_id,
    'ORG',
    d.org_id,
    p.permission,
    'DEFAULT_POLICY',
    NULL,
    NULL,
    'ACTIVE',
    NOW(),
    NOW()
FROM agent_definition d
CROSS JOIN (VALUES ('VIEW'), ('RUN')) AS p(permission)
WHERE d.enabled = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM agent_access_grant g
      WHERE g.org_id = d.org_id
        AND g.agent_id = d.agent_id
        AND g.principal_type = 'ORG'
        AND g.permission = p.permission
        AND g.status = 'ACTIVE'
  );
