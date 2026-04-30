ALTER TABLE knowledge_base
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

ALTER TABLE knowledge_base
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

UPDATE knowledge_base
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE kb_document
    ADD COLUMN IF NOT EXISTS file_size BIGINT;

ALTER TABLE kb_document
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE kb_document
    ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE kb_document
    ADD COLUMN IF NOT EXISTS indexed_at TIMESTAMP;

ALTER TABLE kb_document
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

ALTER TABLE kb_document
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

ALTER TABLE kb_document
    ADD COLUMN IF NOT EXISTS error_message VARCHAR(1000);

ALTER TABLE kb_document
    ADD COLUMN IF NOT EXISTS index_version INT NOT NULL DEFAULT 1;

UPDATE kb_document
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS document_id BIGINT;

ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS chunk_index INT;

ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(128);

ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

UPDATE kb_chunk
SET status = 'ACTIVE'
WHERE status IS NULL;

UPDATE kb_chunk
SET enabled = TRUE
WHERE enabled IS NULL;

CREATE INDEX IF NOT EXISTS idx_kb_chunk_org_doc ON kb_chunk(org_id, document_id);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_org_kb_status ON kb_chunk(org_id, knowledge_base_id, status);

CREATE INDEX IF NOT EXISTS idx_doc_org_kb_status ON kb_document(org_id, knowledge_base_id, status);
