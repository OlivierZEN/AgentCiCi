ALTER TABLE mcp_server
    ADD COLUMN auth_type VARCHAR(48) NOT NULL DEFAULT 'NONE',
    ADD COLUMN token_url VARCHAR(1024),
    ADD COLUMN client_id VARCHAR(256),
    ADD COLUMN client_secret_cipher TEXT,
    ADD COLUMN client_secret_iv TEXT,
    ADD COLUMN token_audience VARCHAR(256),
    ADD COLUMN token_scopes VARCHAR(1000);

ALTER TABLE mcp_server
    ADD CONSTRAINT uq_mcp_server_id_company UNIQUE (id, company_id);

ALTER TABLE mcp_server
    ADD CONSTRAINT ck_mcp_server_auth_type
        CHECK (auth_type IN ('NONE', 'KEYCLOAK_CLIENT_CREDENTIALS')),
    ADD CONSTRAINT ck_mcp_server_keycloak_auth
        CHECK ((auth_type = 'NONE') OR
               (token_url IS NOT NULL AND client_id IS NOT NULL
                AND client_secret_cipher IS NOT NULL AND client_secret_iv IS NOT NULL));

CREATE TABLE application_version_mcp_provider (
    id VARCHAR(64) PRIMARY KEY,
    application_version_id VARCHAR(64) NOT NULL
        REFERENCES internal_application_version(id) ON DELETE CASCADE,
    provider_key VARCHAR(128) NOT NULL,
    transport_type VARCHAR(32) NOT NULL DEFAULT 'streamableHttp',
    auth_type VARCHAR(48) NOT NULL,
    audience VARCHAR(256),
    required_scope VARCHAR(256),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (application_version_id, provider_key),
    CONSTRAINT ck_application_mcp_provider_transport
        CHECK (transport_type = 'streamableHttp'),
    CONSTRAINT ck_application_mcp_provider_auth
        CHECK (auth_type IN ('NONE', 'KEYCLOAK_CLIENT_CREDENTIALS'))
);

CREATE TABLE application_version_mcp_tool (
    id VARCHAR(64) PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL
        REFERENCES application_version_mcp_provider(id) ON DELETE CASCADE,
    tool_name VARCHAR(128) NOT NULL,
    schema_digest VARCHAR(64),
    risk_level VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    confirmation_required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider_id, tool_name),
    CONSTRAINT ck_application_mcp_tool_digest
        CHECK (schema_digest IS NULL OR schema_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_application_mcp_tool_risk
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE TABLE tenant_application_mcp_binding (
    id VARCHAR(64) PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    app_code VARCHAR(64) NOT NULL REFERENCES internal_application(app_code),
    application_version_id VARCHAR(64) NOT NULL,
    provider_key VARCHAR(128) NOT NULL,
    mcp_server_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    bound_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (company_id, app_code, provider_key),
    CONSTRAINT fk_tenant_application_mcp_provider
        FOREIGN KEY (application_version_id, provider_key)
        REFERENCES application_version_mcp_provider(application_version_id, provider_key),
    CONSTRAINT fk_tenant_application_mcp_server
        FOREIGN KEY (mcp_server_id, company_id) REFERENCES mcp_server(id, company_id),
    CONSTRAINT ck_tenant_application_mcp_binding_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_application_version_mcp_provider
    ON application_version_mcp_provider(application_version_id, provider_key);
CREATE INDEX idx_tenant_application_mcp_binding
    ON tenant_application_mcp_binding(company_id, app_code, status);

INSERT INTO application_version_mcp_provider(
    id, application_version_id, provider_key, transport_type, auth_type, audience, required_scope)
VALUES ('catalog-devautopilot-mcp-provider', 'catalog-devautopilot-1-0-0', 'devautopilot.mcp',
        'streamableHttp', 'KEYCLOAK_CLIENT_CREDENTIALS', 'devautopilot-mcp', 'devautopilot:mcp')
ON CONFLICT (application_version_id, provider_key) DO NOTHING;

INSERT INTO application_version_mcp_tool(
    id, provider_id, tool_name, risk_level, confirmation_required)
VALUES
    ('catalog-devautopilot-tool-query', 'catalog-devautopilot-mcp-provider', 'semattice_project_delivery_query', 'LOW', FALSE),
    ('catalog-devautopilot-tool-create', 'catalog-devautopilot-mcp-provider', 'semattice_project_delivery_create', 'HIGH', TRUE),
    ('catalog-devautopilot-tool-update', 'catalog-devautopilot-mcp-provider', 'semattice_project_delivery_update', 'HIGH', TRUE),
    ('catalog-devautopilot-tool-transfer', 'catalog-devautopilot-mcp-provider', 'semattice_project_delivery_transfer', 'HIGH', TRUE),
    ('catalog-devautopilot-tool-delete', 'catalog-devautopilot-mcp-provider', 'semattice_project_delivery_delete', 'HIGH', TRUE),
    ('catalog-devautopilot-tool-review', 'catalog-devautopilot-mcp-provider', 'semattice_project_delivery_review', 'HIGH', TRUE)
ON CONFLICT (provider_id, tool_name) DO NOTHING;
