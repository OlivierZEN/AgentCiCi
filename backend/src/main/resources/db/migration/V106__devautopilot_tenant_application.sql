CREATE TABLE tenant_application_activation (
    id VARCHAR(64) PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    app_code VARCHAR(64) NOT NULL,
    template_version VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    desired_state VARCHAR(32) NOT NULL,
    actual_state VARCHAR(32) NOT NULL,
    semattice_tenant_id VARCHAR(64),
    metadata_version_id VARCHAR(64),
    metadata_digest VARCHAR(128),
    last_error_code VARCHAR(64),
    created_by_member_id VARCHAR(64) NOT NULL REFERENCES company_member(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(company_id, app_code),
    UNIQUE(idempotency_key),
    CONSTRAINT ck_tenant_application_activation_state CHECK (actual_state IN ('PROVISIONING','ACTIVE','SUSPENDED','FAILED'))
);

CREATE TABLE tenant_application_resource (
    id VARCHAR(64) PRIMARY KEY,
    activation_id VARCHAR(64) NOT NULL REFERENCES tenant_application_activation(id) ON DELETE CASCADE,
    logical_role VARCHAR(32) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_alias VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    lifecycle_state VARCHAR(32) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(activation_id, resource_type, external_id),
    UNIQUE(activation_id, resource_alias)
);

CREATE INDEX idx_tenant_application_resource_activation ON tenant_application_resource(activation_id, logical_role);
