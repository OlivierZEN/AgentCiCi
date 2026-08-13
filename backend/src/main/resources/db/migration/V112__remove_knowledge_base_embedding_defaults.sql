-- Runtime embedding invocation is resolved only from the tenant's published scene route.
-- Legacy local placeholders must not be interpreted as a callable provider or model.
ALTER TABLE knowledge_base
    ALTER COLUMN embedding_provider SET DEFAULT 'unconfigured';

ALTER TABLE knowledge_base
    ALTER COLUMN embedding_model SET DEFAULT 'unconfigured';

UPDATE knowledge_base
SET embedding_provider = 'unconfigured'
WHERE embedding_provider IS NULL OR trim(embedding_provider) = '' OR embedding_provider = 'local';

UPDATE knowledge_base
SET embedding_model = 'unconfigured'
WHERE embedding_model IS NULL OR trim(embedding_model) = '' OR embedding_model = 'local-hash';
