ALTER TABLE skill_version
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) NOT NULL DEFAULT 'manual';

ALTER TABLE skill_version
    ADD COLUMN IF NOT EXISTS spec_ir_json TEXT;

ALTER TABLE skill_version
    ADD COLUMN IF NOT EXISTS authoring_notes TEXT;
