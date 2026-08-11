CREATE TABLE tenant_application_member_role (
    id VARCHAR(64) PRIMARY KEY,
    activation_id VARCHAR(64) NOT NULL REFERENCES tenant_application_activation(id) ON DELETE CASCADE,
    company_member_id VARCHAR(64) NOT NULL REFERENCES company_member(id) ON DELETE CASCADE,
    role_code VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    granted_by_member_id VARCHAR(64) NOT NULL REFERENCES company_member(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (activation_id, company_member_id),
    CONSTRAINT ck_tenant_application_member_role_code
        CHECK (role_code IN ('VIEWER', 'CONTRIBUTOR', 'REVIEWER', 'APP_ADMIN')),
    CONSTRAINT ck_tenant_application_member_role_status
        CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE INDEX idx_tenant_application_member_role_lookup
    ON tenant_application_member_role(activation_id, company_member_id, status);

ALTER TABLE agent_service_principal_binding
    DROP CONSTRAINT chk_agent_service_principal_delegation;

ALTER TABLE agent_service_principal_binding
    ADD CONSTRAINT chk_agent_service_principal_delegation
        CHECK (delegation_policy IN ('PRIMARY_OWNER', 'TENANT_APP_ROLE'));

-- Existing DevAutopilot product-manager bindings are upgraded to the tenant application
-- delegation policy. Governance ownership remains unchanged in service_principal_owner.
UPDATE agent_service_principal_binding binding
SET delegation_policy = 'TENANT_APP_ROLE',
    updated_at = CURRENT_TIMESTAMP
WHERE EXISTS (
    SELECT 1
    FROM tenant_application_resource resource
    JOIN tenant_application_activation activation ON activation.id = resource.activation_id
    WHERE activation.company_id = binding.company_id
      AND activation.app_code = 'devautopilot'
      AND resource.resource_type = 'AGENT'
      AND resource.external_id = binding.agent_id
);
