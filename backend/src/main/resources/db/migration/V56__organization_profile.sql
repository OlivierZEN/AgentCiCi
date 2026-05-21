CREATE TABLE IF NOT EXISTS organization_profile (
    org_id VARCHAR(64) PRIMARY KEY,
    short_name VARCHAR(64),
    contact_name VARCHAR(128),
    contact_phone VARCHAR(64),
    contact_email VARCHAR(256),
    website VARCHAR(256),
    industry VARCHAR(128),
    organization_size VARCHAR(64),
    timezone VARCHAR(64),
    notes TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by VARCHAR(64),
    CONSTRAINT fk_organization_profile_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_organization_profile_updated
    ON organization_profile(updated_at DESC);
