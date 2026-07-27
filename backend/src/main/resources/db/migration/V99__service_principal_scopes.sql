CREATE TABLE service_principal_scope (
    service_principal_id VARCHAR(64) NOT NULL REFERENCES service_principal(principal_id) ON DELETE CASCADE,
    scope_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (service_principal_id, scope_code),
    CONSTRAINT chk_service_principal_scope_code CHECK (scope_code ~ '^[a-z][a-z0-9_.-]{1,127}$')
);

CREATE INDEX idx_service_principal_scope_code
    ON service_principal_scope(scope_code);
