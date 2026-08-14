CREATE TABLE IF NOT EXISTS chat_attachment (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    slot_no SMALLINT NOT NULL,
    client_attachment_id VARCHAR(96) NOT NULL,
    message_id BIGINT NULL REFERENCES chat_message(id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_path TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_chat_attachment_public_id UNIQUE (public_id),
    CONSTRAINT uk_chat_attachment_client UNIQUE (company_id, user_id, session_id, client_attachment_id),
    CONSTRAINT uk_chat_attachment_slot UNIQUE (company_id, session_id, slot_no),
    CONSTRAINT ck_chat_attachment_slot CHECK (slot_no BETWEEN 1 AND 10),
    CONSTRAINT ck_chat_attachment_size CHECK (size_bytes BETWEEN 1 AND 20971520),
    CONSTRAINT ck_chat_attachment_status CHECK (status IN ('READY', 'ATTACHED')),
    CONSTRAINT ck_chat_attachment_message_state CHECK (
        (status = 'READY' AND message_id IS NULL)
        OR (status = 'ATTACHED' AND message_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_chat_attachment_session
    ON chat_attachment(company_id, user_id, session_id, slot_no);

CREATE INDEX IF NOT EXISTS idx_chat_attachment_message
    ON chat_attachment(message_id);
