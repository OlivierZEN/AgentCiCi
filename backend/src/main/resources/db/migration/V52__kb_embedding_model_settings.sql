ALTER TABLE knowledge_base
    ADD COLUMN IF NOT EXISTS embedding_provider VARCHAR(64) NOT NULL DEFAULT 'local';

ALTER TABLE knowledge_base
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(128) NOT NULL DEFAULT 'local-hash';

ALTER TABLE knowledge_base
    ADD COLUMN IF NOT EXISTS embedding_dimension INT NOT NULL DEFAULT 1024;
