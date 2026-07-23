CREATE TABLE IF NOT EXISTS memory_candidate (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    subject_id BIGINT NOT NULL,
    scope VARCHAR(32) NOT NULL,
    scope_key VARCHAR(160),
    memory_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    sensitivity VARCHAR(32) NOT NULL,
    confidence NUMERIC(3,2) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP,
    source_type VARCHAR(32) NOT NULL,
    source_refs_json TEXT NOT NULL DEFAULT '[]',
    status VARCHAR(32) NOT NULL,
    reviewed_by VARCHAR(160),
    review_reason TEXT,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_memory_candidate_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_memory_candidate_subject FOREIGN KEY (subject_id) REFERENCES memory_subject(id)
);

CREATE INDEX IF NOT EXISTS idx_memory_candidate_review
    ON memory_candidate(org_id, subject_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS memory_evidence (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    candidate_id BIGINT,
    memory_record_id BIGINT,
    evidence_type VARCHAR(32) NOT NULL,
    reference_value VARCHAR(256) NOT NULL,
    excerpt TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_memory_evidence_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_memory_evidence_candidate FOREIGN KEY (candidate_id) REFERENCES memory_candidate(id),
    CONSTRAINT fk_memory_evidence_record FOREIGN KEY (memory_record_id) REFERENCES memory_record(id),
    CONSTRAINT ck_memory_evidence_target CHECK (candidate_id IS NOT NULL OR memory_record_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_memory_evidence_candidate ON memory_evidence(candidate_id);
CREATE INDEX IF NOT EXISTS idx_memory_evidence_record ON memory_evidence(memory_record_id);
