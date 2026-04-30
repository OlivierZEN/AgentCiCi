ALTER TABLE knowledge_base
    ADD COLUMN IF NOT EXISTS chunk_size INT NOT NULL DEFAULT 280;

ALTER TABLE knowledge_base
    ADD COLUMN IF NOT EXISTS chunk_overlap INT NOT NULL DEFAULT 40;

ALTER TABLE knowledge_base
    ADD COLUMN IF NOT EXISTS chunk_delimiter VARCHAR(32) NOT NULL DEFAULT '\n';

ALTER TABLE knowledge_base
    ADD COLUMN IF NOT EXISTS retrieval_strategy VARCHAR(32) NOT NULL DEFAULT 'VECTOR';

ALTER TABLE knowledge_base
    ADD COLUMN IF NOT EXISTS top_k INT NOT NULL DEFAULT 5;

ALTER TABLE knowledge_base
    ADD COLUMN IF NOT EXISTS score_threshold DOUBLE PRECISION NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS kb_retrieval_log (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    query VARCHAR(2000) NOT NULL,
    retrieval_strategy VARCHAR(32) NOT NULL,
    top_k INT NOT NULL,
    score_threshold DOUBLE PRECISION NOT NULL,
    hit_count INT NOT NULL,
    hit_summary_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_retrieval_log_org_kb ON kb_retrieval_log(org_id, knowledge_base_id);
