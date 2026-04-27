CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS kb_document (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    name VARCHAR(256) NOT NULL,
    content_type VARCHAR(128),
    storage_path VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_org ON knowledge_base(org_id);
CREATE INDEX IF NOT EXISTS idx_doc_org_kb ON kb_document(org_id, knowledge_base_id);
