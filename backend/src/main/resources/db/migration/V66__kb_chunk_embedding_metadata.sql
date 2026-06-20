ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS embedding_provider VARCHAR(64);

ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(128);

ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS embedding_dimension INTEGER;

UPDATE kb_chunk
SET embedding_provider = 'local'
WHERE embedding_provider IS NULL;

UPDATE kb_chunk
SET embedding_model = 'local-hash'
WHERE embedding_model IS NULL;

UPDATE kb_chunk
SET embedding_dimension = 1024
WHERE embedding_dimension IS NULL;
