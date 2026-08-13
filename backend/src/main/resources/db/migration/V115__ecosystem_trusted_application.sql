CREATE TABLE ecosystem_trusted_application (
    app_code VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    keycloak_client_id VARCHAR(128) NOT NULL UNIQUE,
    allowed_scopes VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ecosystem_trusted_application_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_ecosystem_trusted_application_client_status
    ON ecosystem_trusted_application(keycloak_client_id, status);
