CREATE TABLE IF NOT EXISTS platform_policy_bundle (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    bundle_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    version_no INTEGER NOT NULL,
    prompt_fragment TEXT,
    handoff_rules TEXT,
    policy_json TEXT,
    tool_policy_json TEXT,
    data_egress_policy_json TEXT,
    publish_status VARCHAR(32) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_platform_policy_bundle_org_code_version
    ON platform_policy_bundle(org_id, bundle_code, version_no);

CREATE INDEX IF NOT EXISTS idx_platform_policy_bundle_org_code_status
    ON platform_policy_bundle(org_id, bundle_code, publish_status, version_no DESC);
