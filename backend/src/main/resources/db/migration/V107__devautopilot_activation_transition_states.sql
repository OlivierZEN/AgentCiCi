ALTER TABLE tenant_application_activation
    DROP CONSTRAINT ck_tenant_application_activation_state;

ALTER TABLE tenant_application_activation
    ADD CONSTRAINT ck_tenant_application_activation_state
        CHECK (actual_state IN ('PROVISIONING', 'ACTIVE', 'SUSPENDING', 'SUSPENDED', 'RESUMING', 'FAILED'));
