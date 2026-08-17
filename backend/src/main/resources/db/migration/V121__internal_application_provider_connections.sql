CREATE TABLE internal_application_provider_connection (
    binding_key VARCHAR(128) PRIMARY KEY,
    app_code VARCHAR(64) NOT NULL REFERENCES internal_application(app_code),
    display_name VARCHAR(128) NOT NULL,
    environment_key VARCHAR(64) NOT NULL,
    network_scope VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    active_revision_id VARCHAR(64),
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_internal_application_provider_connection_network
        CHECK (network_scope IN ('PUBLIC_HTTPS', 'PLATFORM_INTERNAL')),
    CONSTRAINT ck_internal_application_provider_connection_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'DISABLED')),
    UNIQUE (app_code, display_name, environment_key)
);

CREATE TABLE internal_application_provider_connection_revision (
    id VARCHAR(64) PRIMARY KEY,
    binding_key VARCHAR(128) NOT NULL
        REFERENCES internal_application_provider_connection(binding_key) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    base_url VARCHAR(1024) NOT NULL,
    contract_version VARCHAR(64) NOT NULL,
    auth_type VARCHAR(32) NOT NULL,
    secret_ref VARCHAR(128),
    tls_profile_ref VARCHAR(128),
    health_path VARCHAR(256) NOT NULL,
    activate_path VARCHAR(256),
    reconcile_path VARCHAR(256),
    suspend_path VARCHAR(256),
    resume_path VARCHAR(256),
    upgrade_path VARCHAR(256),
    timeout_ms INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    test_status VARCHAR(32) NOT NULL DEFAULT 'NOT_TESTED',
    last_tested_at TIMESTAMP WITH TIME ZONE,
    last_test_http_status INTEGER,
    last_test_latency_ms BIGINT,
    last_test_error_code VARCHAR(64),
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_internal_application_provider_connection_revision_number
        CHECK (revision_number > 0),
    CONSTRAINT ck_internal_application_provider_connection_auth
        CHECK (auth_type IN ('NONE', 'BEARER_SECRET_REF', 'HMAC_SHA256_SECRET_REF')),
    CONSTRAINT ck_internal_application_provider_connection_secret
        CHECK ((auth_type = 'NONE' AND secret_ref IS NULL)
            OR (auth_type <> 'NONE' AND secret_ref IS NOT NULL)),
    CONSTRAINT ck_internal_application_provider_connection_timeout
        CHECK (timeout_ms BETWEEN 1000 AND 60000),
    CONSTRAINT ck_internal_application_provider_connection_attempts
        CHECK (max_attempts BETWEEN 1 AND 5),
    CONSTRAINT ck_internal_application_provider_connection_test_status
        CHECK (test_status IN ('NOT_TESTED', 'PASSED', 'FAILED')),
    UNIQUE (binding_key, revision_number)
);

ALTER TABLE internal_application_provider_connection
    ADD CONSTRAINT fk_internal_application_provider_connection_active_revision
        FOREIGN KEY (active_revision_id)
        REFERENCES internal_application_provider_connection_revision(id);

CREATE INDEX idx_internal_application_provider_connection_app
    ON internal_application_provider_connection(app_code, status, environment_key);

CREATE INDEX idx_internal_application_provider_connection_revision
    ON internal_application_provider_connection_revision(binding_key, revision_number DESC);

CREATE TABLE tenant_application_operation (
    id VARCHAR(64) PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    app_code VARCHAR(64) NOT NULL REFERENCES internal_application(app_code),
    target_version VARCHAR(64) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    operation_status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    connection_revision_id VARCHAR(64) NOT NULL
        REFERENCES internal_application_provider_connection_revision(id),
    request_digest VARCHAR(64) NOT NULL,
    response_digest VARCHAR(64),
    error_code VARCHAR(64),
    error_summary VARCHAR(500),
    created_by VARCHAR(64) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_tenant_application_operation_type
        CHECK (operation_type IN ('ACTIVATE', 'RECONCILE', 'SUSPEND', 'RESUME', 'UPGRADE')),
    CONSTRAINT ck_tenant_application_operation_status
        CHECK (operation_status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_tenant_application_operation_request_digest
        CHECK (request_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_tenant_application_operation_response_digest
        CHECK (response_digest IS NULL OR response_digest ~ '^[0-9a-f]{64}$'),
    UNIQUE (company_id, app_code, operation_type, idempotency_key)
);

CREATE TABLE tenant_application_operation_step (
    id VARCHAR(64) PRIMARY KEY,
    operation_id VARCHAR(64) NOT NULL REFERENCES tenant_application_operation(id) ON DELETE CASCADE,
    step_code VARCHAR(128) NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    step_status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    response_digest VARCHAR(64),
    error_code VARCHAR(64),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_tenant_application_operation_step_status
        CHECK (step_status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_tenant_application_operation_step_attempts
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_tenant_application_operation_step_digest
        CHECK (response_digest IS NULL OR response_digest ~ '^[0-9a-f]{64}$'),
    UNIQUE (operation_id, step_code)
);

CREATE INDEX idx_tenant_application_operation_lookup
    ON tenant_application_operation(company_id, app_code, created_at DESC);

CREATE INDEX idx_tenant_application_operation_pending
    ON tenant_application_operation(operation_status, updated_at)
    WHERE operation_status IN ('PENDING', 'RUNNING', 'FAILED');
