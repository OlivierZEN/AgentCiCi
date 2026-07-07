CREATE TABLE IF NOT EXISTS kb_quality_rule (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    rule_type VARCHAR(32) NOT NULL,
    pattern TEXT,
    replacement TEXT,
    enabled BOOLEAN NOT NULL,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_quality_rule_org_kb
    ON kb_quality_rule(org_id, knowledge_base_id, enabled);

CREATE TABLE IF NOT EXISTS kb_quality_run (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    scanned_chunk_count INTEGER NOT NULL,
    duplicate_issue_count INTEGER NOT NULL,
    invalid_issue_count INTEGER NOT NULL,
    regex_issue_count INTEGER NOT NULL,
    total_issue_count INTEGER NOT NULL,
    error_message VARCHAR(1000),
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_quality_run_org_kb
    ON kb_quality_run(org_id, knowledge_base_id, created_at DESC);

CREATE TABLE IF NOT EXISTS kb_quality_issue (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    run_id BIGINT,
    issue_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    document_id BIGINT,
    chunk_id BIGINT,
    rule_id BIGINT,
    content_hash VARCHAR(128),
    evidence TEXT,
    status VARCHAR(32) NOT NULL,
    resolved_by VARCHAR(64),
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_quality_issue_org_kb_status
    ON kb_quality_issue(org_id, knowledge_base_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_kb_quality_issue_org_target
    ON kb_quality_issue(org_id, target_type, target_id, status);

CREATE TABLE IF NOT EXISTS kb_annotation_suggestion (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    document_id BIGINT,
    chunk_id BIGINT,
    field_key VARCHAR(64) NOT NULL,
    suggested_value VARCHAR(1024) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    source VARCHAR(32) NOT NULL,
    rationale TEXT,
    status VARCHAR(32) NOT NULL,
    reviewed_by VARCHAR(64),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_annotation_suggestion_org_kb_status
    ON kb_annotation_suggestion(org_id, knowledge_base_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_kb_annotation_suggestion_org_target
    ON kb_annotation_suggestion(org_id, target_type, target_id, status);

CREATE TABLE IF NOT EXISTS kb_chunk_annotation (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    chunk_id BIGINT NOT NULL,
    document_id BIGINT,
    field_key VARCHAR(64) NOT NULL,
    string_value VARCHAR(1024) NOT NULL,
    source VARCHAR(32) NOT NULL,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_chunk_annotation_org_chunk_field
    ON kb_chunk_annotation(org_id, chunk_id, field_key);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_annotation_org_kb
    ON kb_chunk_annotation(org_id, knowledge_base_id, field_key);
