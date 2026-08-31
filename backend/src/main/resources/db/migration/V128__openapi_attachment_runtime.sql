ALTER TABLE agent_api_file
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) NOT NULL DEFAULT 'UPLOAD',
    ADD COLUMN IF NOT EXISTS source_host VARCHAR(255),
    ADD COLUMN IF NOT EXISTS source_url_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS detected_mime_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS file_kind VARCHAR(32),
    ADD COLUMN IF NOT EXISTS sha256 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'READY',
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS import_idempotency_key_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

UPDATE agent_api_file
SET detected_mime_type = mime_type,
    file_kind = CASE WHEN mime_type LIKE 'image/%' THEN 'IMAGE' ELSE 'DOCUMENT' END
WHERE detected_mime_type IS NULL;

CREATE INDEX IF NOT EXISTS idx_agent_api_file_import_idempotency
    ON agent_api_file(company_id, credential_id, agent_id, external_user_id,
                      external_session_id, import_idempotency_key_hash, status);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_api_file_import_idempotency_scope
    ON agent_api_file(company_id, credential_id, agent_id, external_user_id,
                      external_session_id, import_idempotency_key_hash)
    WHERE import_idempotency_key_hash IS NOT NULL AND import_idempotency_key_hash <> '';
