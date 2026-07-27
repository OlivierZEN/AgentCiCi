CREATE TABLE principal (
    id VARCHAR(64) PRIMARY KEY,
    principal_type VARCHAR(16) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    display_name VARCHAR(128),
    created_by_principal_id VARCHAR(64) NULL REFERENCES principal(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    suspended_at TIMESTAMP WITH TIME ZONE NULL,
    revoked_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT chk_principal_type CHECK (principal_type IN ('HUMAN', 'SERVICE')),
    CONSTRAINT chk_principal_lifecycle CHECK (lifecycle_status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'REVOKED'))
);

INSERT INTO principal (id, principal_type, lifecycle_status, display_name, created_at, updated_at, suspended_at, revoked_at)
SELECT id,
       'HUMAN',
       CASE WHEN status = 'ACTIVE' THEN 'ACTIVE' ELSE 'SUSPENDED' END,
       display_name,
       created_at,
       updated_at,
       CASE WHEN status = 'ACTIVE' THEN NULL ELSE updated_at END,
       NULL
FROM user_account;

ALTER TABLE user_account
    ADD CONSTRAINT fk_user_account_principal
    FOREIGN KEY (id) REFERENCES principal(id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE OR REPLACE FUNCTION create_human_principal_for_account()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO principal (id, principal_type, lifecycle_status, display_name, created_at, updated_at)
    VALUES (NEW.id, 'HUMAN', CASE WHEN NEW.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'SUSPENDED' END,
            NEW.display_name, NEW.created_at, NEW.updated_at);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_user_account_create_principal
AFTER INSERT ON user_account
FOR EACH ROW
EXECUTE FUNCTION create_human_principal_for_account();

CREATE TABLE principal_identity (
    id VARCHAR(64) PRIMARY KEY,
    principal_id VARCHAR(64) NOT NULL REFERENCES principal(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    identity_type VARCHAR(32) NOT NULL,
    issuer VARCHAR(512) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    keycloak_client_id VARCHAR(255),
    binding_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_verified_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_principal_identity_type CHECK (identity_type IN ('HUMAN_USER', 'SERVICE_ACCOUNT')),
    CONSTRAINT chk_principal_identity_status CHECK (binding_status IN ('PENDING', 'ACTIVE', 'REVOKED')),
    CONSTRAINT uk_principal_identity_issuer_subject UNIQUE (issuer, subject),
    CONSTRAINT uk_principal_identity_principal_provider UNIQUE (principal_id, provider),
    CONSTRAINT uk_principal_identity_client UNIQUE (keycloak_client_id)
);

INSERT INTO principal_identity (id, principal_id, provider, identity_type, issuer, subject, binding_status, created_at, updated_at)
SELECT id, account_id, 'KEYCLOAK', 'HUMAN_USER', issuer, subject, 'ACTIVE', created_at, updated_at
FROM account_external_identity;

-- account_external_identity remains the compatibility read model used by the
-- existing OIDC callback. Mirror future bindings into the canonical model so
-- the two cannot silently diverge while callers are migrated incrementally.
CREATE OR REPLACE FUNCTION mirror_account_external_identity_to_principal()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO principal_identity (
        id, principal_id, provider, identity_type, issuer, subject,
        binding_status, created_at, updated_at, last_verified_at
    ) VALUES (
        NEW.id, NEW.account_id, 'KEYCLOAK', 'HUMAN_USER', NEW.issuer, NEW.subject,
        'ACTIVE', NEW.created_at, NEW.updated_at, NEW.updated_at
    ) ON CONFLICT (issuer, subject) DO UPDATE
        SET principal_id = EXCLUDED.principal_id,
            binding_status = 'ACTIVE',
            updated_at = EXCLUDED.updated_at,
            last_verified_at = EXCLUDED.last_verified_at;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_account_external_identity_mirror_principal
AFTER INSERT OR UPDATE ON account_external_identity
FOR EACH ROW
EXECUTE FUNCTION mirror_account_external_identity_to_principal();

CREATE TABLE service_principal (
    principal_id VARCHAR(64) PRIMARY KEY REFERENCES principal(id) ON DELETE CASCADE,
    public_id VARCHAR(13) NOT NULL UNIQUE,
    service_kind VARCHAR(32) NOT NULL,
    client_id VARCHAR(255) NOT NULL UNIQUE,
    credential_mode VARCHAR(32) NOT NULL,
    token_audience VARCHAR(255) NOT NULL,
    credential_expires_at TIMESTAMP WITH TIME ZONE,
    last_rotated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_service_principal_public_id CHECK (public_id ~ '^S[0-9]{4}[A-Z0-9]{8}$'),
    CONSTRAINT chk_service_principal_kind CHECK (service_kind IN ('OFFICIAL_APP', 'THIRD_PARTY', 'AUTOMATION', 'SYSTEM')),
    CONSTRAINT chk_service_principal_credential CHECK (credential_mode IN ('CLIENT_SECRET', 'PRIVATE_KEY_JWT', 'MTLS'))
);

CREATE TABLE service_principal_owner (
    service_principal_id VARCHAR(64) NOT NULL REFERENCES service_principal(principal_id) ON DELETE CASCADE,
    owner_principal_id VARCHAR(64) NOT NULL REFERENCES principal(id),
    company_member_id VARCHAR(64) NOT NULL REFERENCES company_member(id),
    owner_role VARCHAR(16) NOT NULL,
    owner_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (service_principal_id, owner_principal_id, company_member_id),
    CONSTRAINT chk_service_principal_owner_role CHECK (owner_role IN ('PRIMARY', 'MAINTAINER')),
    CONSTRAINT chk_service_principal_owner_status CHECK (owner_status IN ('ACTIVE', 'TRANSFER_REQUIRED', 'REVOKED'))
);

CREATE UNIQUE INDEX uk_service_principal_primary_owner
    ON service_principal_owner(service_principal_id)
    WHERE owner_role = 'PRIMARY' AND owner_status = 'ACTIVE';

CREATE TABLE principal_provisioning_operation (
    id VARCHAR(64) PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    requested_by_principal_id VARCHAR(64) REFERENCES principal(id),
    target_company_id VARCHAR(64) REFERENCES company(id),
    request_type VARCHAR(64) NOT NULL,
    operation_state VARCHAR(32) NOT NULL,
    result_principal_id VARCHAR(64) REFERENCES principal(id),
    result_member_id VARCHAR(64) REFERENCES company_member(id),
    failure_code VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_principal_provisioning_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_principal_provisioning_type CHECK (request_type IN ('ENSURE_HUMAN_MEMBER', 'ENSURE_SERVICE_PRINCIPAL', 'TRANSFER_SERVICE_OWNER')),
    CONSTRAINT chk_principal_provisioning_state CHECK (operation_state IN ('RECEIVED', 'RESOLVING', 'KEYCLOAK_PENDING', 'IDENTITY_BOUND', 'INVITATION_SENT', 'ACTIVATED', 'COMPENSATING', 'FAILED', 'CANCELLED'))
);
