-- V98 mirrored account_external_identity into principal_identity by issuer and
-- subject. Rebinding a deleted Keycloak user changes the subject on the same
-- local identity row, which must update that row's canonical mirror instead of
-- trying to insert its already-used primary key.
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
    ) ON CONFLICT (id) DO UPDATE
        SET principal_id = EXCLUDED.principal_id,
            provider = EXCLUDED.provider,
            identity_type = EXCLUDED.identity_type,
            issuer = EXCLUDED.issuer,
            subject = EXCLUDED.subject,
            binding_status = 'ACTIVE',
            updated_at = EXCLUDED.updated_at,
            last_verified_at = EXCLUDED.last_verified_at;
    RETURN NEW;
END;
$$;
