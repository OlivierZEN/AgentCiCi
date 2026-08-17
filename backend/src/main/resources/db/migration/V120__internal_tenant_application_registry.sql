CREATE TABLE internal_application (
    app_code VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    icon_key VARCHAR(64) NOT NULL,
    owner_team VARCHAR(128) NOT NULL,
    tenant_mode VARCHAR(64) NOT NULL,
    catalog_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    trusted_app_code VARCHAR(64),
    launch_mode VARCHAR(32) NOT NULL DEFAULT 'NONE',
    launch_route_key VARCHAR(128),
    default_version VARCHAR(64),
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_internal_application_tenant_mode
        CHECK (tenant_mode IN ('PLATFORM_BASE', 'SHARED_RUNTIME_TENANT_ISOLATED')),
    CONSTRAINT ck_internal_application_catalog_status
        CHECK (catalog_status IN ('DRAFT', 'PUBLISHED', 'SUSPENDED', 'RETIRED')),
    CONSTRAINT ck_internal_application_launch_mode
        CHECK (launch_mode IN ('NONE', 'PLATFORM_ROUTE', 'SERVER_HANDOFF')),
    CONSTRAINT ck_internal_application_launch_route
        CHECK ((launch_mode = 'NONE' AND launch_route_key IS NULL)
            OR (launch_mode <> 'NONE' AND launch_route_key IS NOT NULL))
);

CREATE TABLE internal_application_version (
    id VARCHAR(64) PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL REFERENCES internal_application(app_code),
    version VARCHAR(64) NOT NULL,
    manifest_schema_version VARCHAR(64) NOT NULL,
    provider_binding_key VARCHAR(128),
    initialization_engine VARCHAR(32) NOT NULL,
    manifest_json JSONB NOT NULL,
    manifest_digest VARCHAR(64) NOT NULL,
    version_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(64) NOT NULL,
    validated_by VARCHAR(64),
    validated_at TIMESTAMP WITH TIME ZONE,
    published_by VARCHAR(64),
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (app_code, version),
    CONSTRAINT ck_internal_application_version_engine
        CHECK (initialization_engine IN ('NONE', 'SAGA_V1')),
    CONSTRAINT ck_internal_application_version_status
        CHECK (version_status IN ('DRAFT', 'VALIDATED', 'PUBLISHED', 'DEPRECATED', 'REVOKED')),
    CONSTRAINT ck_internal_application_version_digest
        CHECK (manifest_digest ~ '^[0-9a-f]{64}$')
);

CREATE TABLE internal_application_dependency (
    id VARCHAR(64) PRIMARY KEY,
    application_version_id VARCHAR(64) NOT NULL REFERENCES internal_application_version(id) ON DELETE CASCADE,
    dependency_app_code VARCHAR(64) NOT NULL REFERENCES internal_application(app_code),
    version_constraint VARCHAR(64) NOT NULL,
    dependency_type VARCHAR(32) NOT NULL,
    activation_policy VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (application_version_id, dependency_app_code),
    CONSTRAINT ck_internal_application_dependency_type
        CHECK (dependency_type IN ('REQUIRED_ACTIVATION', 'REQUIRED_RUNTIME', 'OPTIONAL')),
    CONSTRAINT ck_internal_application_activation_policy
        CHECK (activation_policy IN ('REQUIRE_EXISTING', 'AUTO_PROVISION_ALLOWED'))
);

CREATE INDEX idx_internal_application_catalog
    ON internal_application(catalog_status, display_name);

CREATE INDEX idx_internal_application_version_catalog
    ON internal_application_version(app_code, version_status, published_at DESC);

CREATE INDEX idx_internal_application_dependency_lookup
    ON internal_application_dependency(dependency_app_code, application_version_id);

INSERT INTO internal_application(
    app_code, display_name, summary, icon_key, owner_team, tenant_mode, catalog_status,
    launch_mode, launch_route_key, default_version, created_by)
VALUES
    ('agentcici', 'AgentCiCi 智能体平台', '租户默认开通的智能体运行与治理应用', 'bot',
     'AgentCiCi', 'PLATFORM_BASE', 'PUBLISHED', 'PLATFORM_ROUTE', 'agentcici.lifecycle', '1.0.0', 'platform-seed'),
    ('semattice', 'Semattice 业务数据与语义运行底座', '面向智能体的业务数据与语义运行底座', 'boxes',
     'Semattice', 'SHARED_RUNTIME_TENANT_ISOLATED', 'PUBLISHED', 'NONE', NULL, '1.0.0', 'platform-seed'),
    ('devautopilot', 'DevAutopilot 研发交付系统', '受治理的 AI Coding Agent 研发交付控制台', 'workflow',
     'DevAutopilot', 'SHARED_RUNTIME_TENANT_ISOLATED', 'PUBLISHED', 'SERVER_HANDOFF', 'devautopilot.web', '1.0.0', 'platform-seed')
ON CONFLICT (app_code) DO NOTHING;

INSERT INTO internal_application_version(
    id, app_code, version, manifest_schema_version, provider_binding_key,
    initialization_engine, manifest_json, manifest_digest, version_status,
    created_by, validated_by, validated_at, published_by, published_at)
VALUES
    ('catalog-agentcici-1-0-0', 'agentcici', '1.0.0', 'tenant-application/v1', NULL, 'NONE',
     '{"schemaVersion":"tenant-application/v1","initializationEngine":"NONE","steps":[]}'::jsonb,
     '13d86225c2f8f980fa05d593bf1e30a6fea6ee903be70d2ef2d0d4af55ca6596', 'PUBLISHED',
     'platform-seed', 'platform-seed', CURRENT_TIMESTAMP, 'platform-seed', CURRENT_TIMESTAMP),
    ('catalog-semattice-1-0-0', 'semattice', '1.0.0', 'tenant-application/v1', 'semattice.provisioning', 'SAGA_V1',
     '{"schemaVersion":"tenant-application/v1","providerBindingKey":"semattice.provisioning","initializationEngine":"SAGA_V1","steps":[{"code":"tenant-provisioning","type":"PROVIDER_CALLBACK","capability":"tenant.provision","contractVersion":"v1"}]}'::jsonb,
     'd82c9681704deb20d10f7f88452cc4617db56bc3f46dd4926f932b7f9fa684d0', 'PUBLISHED',
     'platform-seed', 'platform-seed', CURRENT_TIMESTAMP, 'platform-seed', CURRENT_TIMESTAMP),
    ('catalog-devautopilot-1-0-0', 'devautopilot', '1.0.0', 'tenant-application/v1', 'devautopilot.lifecycle', 'SAGA_V1',
     '{"schemaVersion":"tenant-application/v1","providerBindingKey":"devautopilot.lifecycle","initializationEngine":"SAGA_V1","steps":[{"code":"metadata","type":"DEPENDENCY_CAPABILITY","capability":"semattice.template.apply","contractVersion":"v1"},{"code":"product-manager","type":"PLATFORM_CAPABILITY","capability":"agent.blueprint.ensure","contractVersion":"v1"},{"code":"principals","type":"DEPENDENCY_CAPABILITY","capability":"identity.principal.sync","contractVersion":"v1"},{"code":"authorization","type":"DEPENDENCY_CAPABILITY","capability":"authorization.template.apply","contractVersion":"v1"},{"code":"activation","type":"PROVIDER_CALLBACK","capability":"tenant.activate","contractVersion":"v1"}]}'::jsonb,
     'de97c39c837f5c62f349cddf72d74088da6500fef22c4bf98afda80aa1806153', 'PUBLISHED',
     'platform-seed', 'platform-seed', CURRENT_TIMESTAMP, 'platform-seed', CURRENT_TIMESTAMP)
ON CONFLICT (app_code, version) DO NOTHING;

INSERT INTO internal_application_dependency(
    id, application_version_id, dependency_app_code, version_constraint,
    dependency_type, activation_policy)
VALUES
    ('catalog-devautopilot-dependency-semattice', 'catalog-devautopilot-1-0-0', 'semattice', '>=1.0.0',
     'REQUIRED_RUNTIME', 'REQUIRE_EXISTING')
ON CONFLICT (application_version_id, dependency_app_code) DO NOTHING;
