CREATE TABLE IF NOT EXISTS organization_retention_policy (
    org_id VARCHAR(64) PRIMARY KEY,
    grace_until TIMESTAMP,
    suspend_until TIMESTAMP,
    export_deadline TIMESTAMP,
    purge_after TIMESTAMP,
    legal_hold BOOLEAN NOT NULL DEFAULT FALSE,
    policy_source VARCHAR(64) NOT NULL DEFAULT 'PLATFORM_MANUAL',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS organization_purge_job (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    dry_run BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL,
    phase VARCHAR(64) NOT NULL,
    requested_by VARCHAR(64) NOT NULL,
    reason VARCHAR(512),
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    error_message TEXT,
    manifest_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_organization_purge_job_org_created
    ON organization_purge_job(org_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_organization_purge_job_status
    ON organization_purge_job(status, created_at DESC);
