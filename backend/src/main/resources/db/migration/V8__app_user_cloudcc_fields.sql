ALTER TABLE organization_member ADD COLUMN IF NOT EXISTS nickname VARCHAR(128);
ALTER TABLE organization_member ADD COLUMN IF NOT EXISTS cc_username VARCHAR(128);
ALTER TABLE organization_member ADD COLUMN IF NOT EXISTS cc_safetymark VARCHAR(128);
