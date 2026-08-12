ALTER TABLE tenant_application_activation
    ADD COLUMN authorization_template_version VARCHAR(100),
    ADD COLUMN authorization_digest VARCHAR(64),
    ADD COLUMN authorization_role_count INTEGER,
    ADD COLUMN authorization_permission_set_count INTEGER,
    ADD COLUMN authorization_assignment_count INTEGER,
    ADD COLUMN authorization_verified_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE tenant_application_activation
    ADD CONSTRAINT ck_tenant_application_authorization_counts
    CHECK (
        (authorization_role_count IS NULL OR authorization_role_count >= 0)
        AND (authorization_permission_set_count IS NULL OR authorization_permission_set_count >= 0)
        AND (authorization_assignment_count IS NULL OR authorization_assignment_count >= 0)
    );
