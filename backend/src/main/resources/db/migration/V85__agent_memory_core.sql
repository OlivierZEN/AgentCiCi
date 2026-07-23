CREATE TABLE IF NOT EXISTS memory_subject (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    application_code VARCHAR(96) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    external_ref VARCHAR(160) NOT NULL,
    identity_level VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_memory_subject_identity UNIQUE (org_id, application_code, subject_type, external_ref),
    CONSTRAINT fk_memory_subject_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_memory_subject_lookup
    ON memory_subject(org_id, application_code, external_ref);

CREATE TABLE IF NOT EXISTS memory_record (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    subject_id BIGINT NOT NULL,
    scope VARCHAR(32) NOT NULL,
    scope_key VARCHAR(160),
    memory_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    sensitivity VARCHAR(32) NOT NULL,
    confidence NUMERIC(3,2) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP,
    source_type VARCHAR(32) NOT NULL,
    source_refs_json TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_memory_record_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_memory_record_subject FOREIGN KEY (subject_id) REFERENCES memory_subject(id)
);

CREATE INDEX IF NOT EXISTS idx_memory_record_context
    ON memory_record(org_id, subject_id, status, valid_to, updated_at DESC);

CREATE TABLE IF NOT EXISTS memory_conversation_snapshot (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    application_code VARCHAR(96) NOT NULL,
    conversation_ref VARCHAR(160) NOT NULL,
    subject_id BIGINT NOT NULL,
    active_agent_id VARCHAR(64),
    summary TEXT NOT NULL,
    state_json TEXT NOT NULL DEFAULT '{}',
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_memory_conversation_snapshot UNIQUE (org_id, application_code, conversation_ref),
    CONSTRAINT fk_memory_snapshot_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_memory_snapshot_subject FOREIGN KEY (subject_id) REFERENCES memory_subject(id)
);

CREATE INDEX IF NOT EXISTS idx_memory_conversation_subject
    ON memory_conversation_snapshot(org_id, subject_id, updated_at DESC);
