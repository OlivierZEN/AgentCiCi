ALTER TABLE organization_purge_job
    ADD COLUMN IF NOT EXISTS worker_id VARCHAR(128);

ALTER TABLE organization_purge_job
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP;

ALTER TABLE organization_purge_job
    ADD COLUMN IF NOT EXISTS lock_expires_at TIMESTAMP;

ALTER TABLE organization_purge_job
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE organization_purge_job
    ADD COLUMN IF NOT EXISTS dead_letter_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_organization_purge_job_claim
    ON organization_purge_job(status, dry_run, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_organization_purge_job_lease
    ON organization_purge_job(status, dry_run, lock_expires_at ASC);
