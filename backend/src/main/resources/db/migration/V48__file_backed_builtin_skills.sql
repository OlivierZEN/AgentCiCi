ALTER TABLE platform_skill_template
    ADD COLUMN IF NOT EXISTS resource_type VARCHAR(32) DEFAULT 'INLINE';

ALTER TABLE platform_skill_template
    ADD COLUMN IF NOT EXISTS resource_uri VARCHAR(512);

ALTER TABLE platform_skill_template
    ADD COLUMN IF NOT EXISTS bundle_checksum VARCHAR(128);

ALTER TABLE platform_skill_template_version
    ADD COLUMN IF NOT EXISTS resource_uri VARCHAR(512);

ALTER TABLE platform_skill_template_version
    ADD COLUMN IF NOT EXISTS bundle_checksum VARCHAR(128);

ALTER TABLE platform_skill_template_version
    ADD COLUMN IF NOT EXISTS entrypoint_checksum VARCHAR(128);

ALTER TABLE platform_skill_template_version
    ADD COLUMN IF NOT EXISTS module_manifest_json TEXT;

UPDATE platform_skill_template
SET resource_type = COALESCE(resource_type, 'INLINE')
WHERE resource_type IS NULL;

CREATE INDEX IF NOT EXISTS idx_platform_skill_template_resource_type
    ON platform_skill_template(org_id, resource_type);
