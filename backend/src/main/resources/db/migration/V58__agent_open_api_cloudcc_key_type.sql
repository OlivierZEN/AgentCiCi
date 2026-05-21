ALTER TABLE agent_api_credential
    ADD COLUMN IF NOT EXISTS key_type VARCHAR(32) NOT NULL DEFAULT 'standard';

UPDATE agent_api_credential
SET key_type = 'standard'
WHERE key_type IS NULL OR TRIM(key_type) = '';
