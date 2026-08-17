CREATE TABLE IF NOT EXISTS platform_tenant_provisioning_request (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(96) NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    company_id VARCHAR(64),
    owner_member_id VARCHAR(64),
    owner_account_id VARCHAR(64),
    owner_resolution VARCHAR(32),
    reused_existing_account BOOLEAN,
    owner_activation_required BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_tenant_provisioning_company
        FOREIGN KEY (company_id) REFERENCES company(id)
);

CREATE INDEX IF NOT EXISTS idx_platform_tenant_provisioning_company
    ON platform_tenant_provisioning_request(company_id);
