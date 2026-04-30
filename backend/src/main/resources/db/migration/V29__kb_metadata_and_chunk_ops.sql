CREATE TABLE IF NOT EXISTS kb_metadata_field (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    field_key VARCHAR(64) NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    value_type VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_metadata_field_org_kb_key
    ON kb_metadata_field(org_id, knowledge_base_id, field_key);

CREATE TABLE IF NOT EXISTS kb_document_metadata (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    field_key VARCHAR(64) NOT NULL,
    string_value VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_doc_metadata_org_kb_doc_key
    ON kb_document_metadata(org_id, knowledge_base_id, document_id, field_key);

CREATE INDEX IF NOT EXISTS idx_kb_doc_metadata_org_kb_doc
    ON kb_document_metadata(org_id, knowledge_base_id, document_id);
