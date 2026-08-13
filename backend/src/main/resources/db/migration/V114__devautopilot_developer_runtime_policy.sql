ALTER TABLE tenant_application_resource
    ADD COLUMN max_instances SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN runtime_policy_revision BIGINT NOT NULL DEFAULT 1;

ALTER TABLE tenant_application_resource
    ADD CONSTRAINT ck_tenant_application_resource_max_instances
        CHECK (max_instances BETWEEN 1 AND 64),
    ADD CONSTRAINT ck_tenant_application_resource_runtime_policy_revision
        CHECK (runtime_policy_revision >= 1);

COMMENT ON COLUMN tenant_application_resource.max_instances IS
    'Maximum concurrent execution leases for a machine developer; authoritative only for developer SERVICE_PRINCIPAL resources.';
COMMENT ON COLUMN tenant_application_resource.runtime_policy_revision IS
    'Optimistic-lock revision for mutable machine runtime policy.';
