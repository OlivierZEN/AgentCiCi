ALTER TABLE tenant_application_activation
    ADD COLUMN activation_stage VARCHAR(32) NOT NULL DEFAULT 'PROVISIONING',
    ADD COLUMN failed_stage VARCHAR(32),
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN lease_token VARCHAR(64),
    ADD COLUMN lease_expires_at TIMESTAMP WITH TIME ZONE;

UPDATE tenant_application_activation
SET activation_stage = CASE
        WHEN actual_state = 'ACTIVE' THEN 'ACTIVE'
        WHEN authorization_verified_at IS NOT NULL THEN 'AUTHORIZATION_READY'
        WHEN metadata_version_id IS NOT NULL THEN 'METADATA_READY'
        ELSE 'PROVISIONING'
    END,
    attempt_count = CASE WHEN actual_state = 'ACTIVE' THEN 1 ELSE 0 END,
    last_attempt_at = updated_at;

ALTER TABLE tenant_application_activation
    ADD CONSTRAINT ck_tenant_application_activation_stage
        CHECK (activation_stage IN (
            'PROVISIONING', 'METADATA_READY', 'PRODUCT_MANAGER_READY',
            'PRINCIPALS_READY', 'AUTHORIZATION_READY', 'ACTIVE'
        )),
    ADD CONSTRAINT ck_tenant_application_failed_stage
        CHECK (failed_stage IS NULL OR failed_stage IN (
            'PROVISIONING', 'METADATA_READY', 'PRODUCT_MANAGER_READY',
            'PRINCIPALS_READY', 'AUTHORIZATION_READY', 'ACTIVE'
        )),
    ADD CONSTRAINT ck_tenant_application_attempt_count
        CHECK (attempt_count >= 0),
    ADD CONSTRAINT ck_tenant_application_activation_lease
        CHECK ((lease_token IS NULL) = (lease_expires_at IS NULL));

CREATE INDEX idx_tenant_application_activation_recovery
    ON tenant_application_activation(actual_state, activation_stage, updated_at)
    WHERE actual_state IN ('PROVISIONING', 'FAILED');
