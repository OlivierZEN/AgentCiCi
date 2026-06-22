CREATE TABLE IF NOT EXISTS kb_access_grant (
    id VARCHAR(64) PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    document_id BIGINT,
    chunk_id BIGINT,
    principal_type VARCHAR(32) NOT NULL,
    principal_id VARCHAR(128),
    permission VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    granted_by VARCHAR(64),
    expires_at TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_access_grant_doc
    ON kb_access_grant(org_id, knowledge_base_id, document_id, status);

CREATE INDEX IF NOT EXISTS idx_kb_access_grant_chunk
    ON kb_access_grant(org_id, knowledge_base_id, chunk_id, status);

CREATE INDEX IF NOT EXISTS idx_kb_access_grant_principal
    ON kb_access_grant(org_id, principal_type, principal_id, status);
