ALTER TABLE skill_version
    ADD COLUMN IF NOT EXISTS change_log TEXT;

ALTER TABLE skill_version
    ADD COLUMN IF NOT EXISTS diff_summary TEXT;

ALTER TABLE skill_version
    ADD COLUMN IF NOT EXISTS version_source VARCHAR(32);

ALTER TABLE skill_version
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(128);

ALTER TABLE skill_version
    ADD COLUMN IF NOT EXISTS restore_visible BOOLEAN;

ALTER TABLE skill_version
    ADD COLUMN IF NOT EXISTS retention_state VARCHAR(32);

ALTER TABLE skill_version
    ADD COLUMN IF NOT EXISTS restored_from_version_id BIGINT;

ALTER TABLE skill_version
    ADD COLUMN IF NOT EXISTS package_manifest_json TEXT;

UPDATE skill_version
SET change_log = COALESCE(change_log, '保存技能配置'),
    version_source = COALESCE(version_source, UPPER(COALESCE(source_type, 'SAVE'))),
    created_by = COALESCE(created_by, 'system'),
    restore_visible = COALESCE(restore_visible, TRUE),
    retention_state = COALESCE(retention_state, 'ACTIVE_RECENT')
WHERE change_log IS NULL
   OR version_source IS NULL
   OR created_by IS NULL
   OR restore_visible IS NULL
   OR retention_state IS NULL;

ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(32);

ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(128);

ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS delete_reason TEXT;

ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS last_published_at TIMESTAMP;

ALTER TABLE skill_definition
    ADD COLUMN IF NOT EXISTS last_published_by VARCHAR(128);

UPDATE skill_definition
SET lifecycle_status = CASE
    WHEN enabled THEN 'DRAFT'
    ELSE 'DISABLED'
END
WHERE lifecycle_status IS NULL;

CREATE INDEX IF NOT EXISTS idx_skill_version_org_skill_restore
    ON skill_version(org_id, skill_id, restore_visible, version_no DESC);

CREATE INDEX IF NOT EXISTS idx_skill_definition_org_lifecycle
    ON skill_definition(org_id, lifecycle_status);
