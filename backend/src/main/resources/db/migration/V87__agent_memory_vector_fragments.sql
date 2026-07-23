CREATE TABLE IF NOT EXISTS memory_vector_fragment (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    memory_record_id BIGINT NOT NULL UNIQUE,
    vector_id VARCHAR(160) NOT NULL,
    redacted_text TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    indexed_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_memory_vector_fragment_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_memory_vector_fragment_record FOREIGN KEY (memory_record_id) REFERENCES memory_record(id)
);
CREATE INDEX IF NOT EXISTS idx_memory_vector_fragment_lookup ON memory_vector_fragment(org_id, status, memory_record_id);
