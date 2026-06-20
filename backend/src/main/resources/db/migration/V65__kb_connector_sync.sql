CREATE TABLE IF NOT EXISTS kb_data_source (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    name VARCHAR(160) NOT NULL,
    config_json TEXT,
    sync_cursor TEXT,
    status VARCHAR(32) NOT NULL,
    last_synced_at TIMESTAMP,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_data_source_org_kb
    ON kb_data_source(org_id, knowledge_base_id, status);

CREATE TABLE IF NOT EXISTS kb_sync_job (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    sync_cursor_before TEXT,
    sync_cursor_after TEXT,
    document_count INTEGER NOT NULL,
    chunk_count INTEGER NOT NULL,
    error_message VARCHAR(1000),
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_sync_job_org_source
    ON kb_sync_job(org_id, data_source_id, created_at DESC);

CREATE TABLE IF NOT EXISTS kb_source_document_map (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    external_document_id VARCHAR(256) NOT NULL,
    document_id BIGINT NOT NULL,
    source_hash VARCHAR(128),
    last_synced_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_source_doc_org_source_external
    ON kb_source_document_map(org_id, data_source_id, external_document_id);
