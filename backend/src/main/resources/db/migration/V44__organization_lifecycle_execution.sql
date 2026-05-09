ALTER TABLE organization_retention_policy
    ADD COLUMN IF NOT EXISTS legal_hold_reason VARCHAR(512);

ALTER TABLE organization_retention_policy
    ADD COLUMN IF NOT EXISTS legal_hold_approved_by VARCHAR(64);

ALTER TABLE organization_retention_policy
    ADD COLUMN IF NOT EXISTS legal_hold_approved_at TIMESTAMP;

ALTER TABLE organization_retention_policy
    ADD COLUMN IF NOT EXISTS legal_hold_review_at TIMESTAMP;

ALTER TABLE organization_purge_job
    ADD COLUMN IF NOT EXISTS source_dry_run_job_id BIGINT;

ALTER TABLE organization_purge_job
    ADD COLUMN IF NOT EXISTS confirmation_text VARCHAR(128);

ALTER TABLE organization_purge_job
    ADD COLUMN IF NOT EXISTS manifest_version VARCHAR(32) NOT NULL DEFAULT 'v1';

ALTER TABLE organization_purge_job
    ADD COLUMN IF NOT EXISTS manifest_hash VARCHAR(128);

ALTER TABLE organization_purge_job
    ADD COLUMN IF NOT EXISTS result_json TEXT NOT NULL DEFAULT '{}';

CREATE TABLE IF NOT EXISTS organization_export_job (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_by VARCHAR(64) NOT NULL,
    reason VARCHAR(512),
    file_path VARCHAR(1024),
    manifest_json TEXT NOT NULL DEFAULT '{}',
    error_message TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_organization_export_job_org_created
    ON organization_export_job(org_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_organization_export_job_status
    ON organization_export_job(status, created_at DESC);
