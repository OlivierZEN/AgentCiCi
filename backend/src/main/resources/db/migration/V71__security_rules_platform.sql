CREATE TABLE IF NOT EXISTS security_rule (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    rule_type VARCHAR(32) NOT NULL,
    category VARCHAR(64) NOT NULL,
    match_type VARCHAR(32) NOT NULL,
    pattern_text TEXT NOT NULL,
    severity VARCHAR(16) NOT NULL,
    action VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(500) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_security_rule_org_enabled_updated
    ON security_rule (org_id, enabled, updated_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS security_detection_event (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    surface VARCHAR(64) NOT NULL,
    action VARCHAR(16) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    category VARCHAR(64) NOT NULL,
    rule_name VARCHAR(128) NOT NULL DEFAULT '',
    matched_summary VARCHAR(500) NOT NULL DEFAULT '',
    redacted_text TEXT NOT NULL DEFAULT '',
    policy_version VARCHAR(64) NOT NULL DEFAULT 'builtin-v1',
    reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    review_result VARCHAR(32) NOT NULL DEFAULT '',
    review_note VARCHAR(500) NOT NULL DEFAULT '',
    reviewed_by VARCHAR(64) NOT NULL DEFAULT '',
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_security_detection_event_org_created
    ON security_detection_event (org_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_security_detection_event_org_reviewed
    ON security_detection_event (org_id, reviewed, created_at DESC);

CREATE TABLE IF NOT EXISTS security_review_item (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    event_id BIGINT NOT NULL REFERENCES security_detection_event(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    assignee VARCHAR(64) NOT NULL DEFAULT '',
    note VARCHAR(500) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_security_review_item_org_status
    ON security_review_item (org_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS security_policy_snapshot (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_security_policy_snapshot_org_version
    ON security_policy_snapshot (org_id, policy_version, created_at DESC);
