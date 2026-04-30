ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32);

ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(32);

ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS edit_policy VARCHAR(32);

ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS binding_policy VARCHAR(32);

ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS update_policy VARCHAR(32);

ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS template_code VARCHAR(64);

ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS base_template_version INTEGER;

ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS current_published_version_id BIGINT;

ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS latest_draft_version_id BIGINT;

UPDATE skill_definition
SET source_type = CASE
    WHEN builtin THEN 'PLATFORM_STANDARD'
    ELSE 'TENANT_CUSTOM'
END
WHERE source_type IS NULL;

UPDATE skill_definition
SET visibility = CASE
    WHEN skill_code IN ('conversation-core', 'knowledge-first', 'safe-handoff') THEN 'HIDDEN'
    ELSE 'VISIBLE'
END
WHERE visibility IS NULL;

UPDATE skill_definition
SET edit_policy = CASE
    WHEN skill_code IN ('conversation-core', 'knowledge-first', 'safe-handoff') THEN 'LOCKED'
    WHEN builtin THEN 'CONFIGURABLE'
    ELSE 'EDITABLE'
END
WHERE edit_policy IS NULL;

UPDATE skill_definition
SET binding_policy = CASE
    WHEN skill_code IN ('conversation-core', 'knowledge-first', 'safe-handoff') THEN 'MANDATORY'
    ELSE 'OPTIONAL'
END
WHERE binding_policy IS NULL;

UPDATE skill_definition
SET update_policy = CASE
    WHEN builtin THEN 'AUTO'
    ELSE 'MANUAL'
END
WHERE update_policy IS NULL;

UPDATE skill_definition
SET template_code = skill_code
WHERE builtin
  AND (template_code IS NULL OR TRIM(template_code) = '');

ALTER TABLE skill_definition
    ALTER COLUMN source_type SET NOT NULL;

ALTER TABLE skill_definition
    ALTER COLUMN visibility SET NOT NULL;

ALTER TABLE skill_definition
    ALTER COLUMN edit_policy SET NOT NULL;

ALTER TABLE skill_definition
    ALTER COLUMN binding_policy SET NOT NULL;

ALTER TABLE skill_definition
    ALTER COLUMN update_policy SET NOT NULL;

CREATE TABLE IF NOT EXISTS platform_audit_log (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_key VARCHAR(128) NOT NULL,
    detail VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_platform_audit_log_org_created
    ON platform_audit_log(org_id, created_at DESC);
