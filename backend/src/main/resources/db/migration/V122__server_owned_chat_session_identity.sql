-- Conversation history in DEV/UAT/production currently belongs only to test accounts.
-- Reset it instead of preserving ambiguous client-owned identifiers across the new identity boundary.
TRUNCATE TABLE chat_attachment RESTART IDENTITY CASCADE;
TRUNCATE TABLE chat_message RESTART IDENTITY CASCADE;
TRUNCATE TABLE chat_session_state RESTART IDENTITY CASCADE;
TRUNCATE TABLE agent_run_trace RESTART IDENTITY CASCADE;
TRUNCATE TABLE agent_api_message RESTART IDENTITY CASCADE;
TRUNCATE TABLE agent_api_call_log RESTART IDENTITY CASCADE;
TRUNCATE TABLE agent_api_session_map RESTART IDENTITY CASCADE;
TRUNCATE TABLE wecom_kf_message RESTART IDENTITY CASCADE;
TRUNCATE TABLE wecom_kf_conversation RESTART IDENTITY CASCADE;
TRUNCATE TABLE chat_session RESTART IDENTITY CASCADE;

ALTER TABLE chat_session
    ADD COLUMN channel_code VARCHAR(32) NOT NULL DEFAULT 'web',
    ADD COLUMN visibility_scope VARCHAR(16) NOT NULL DEFAULT 'USER',
    ADD COLUMN source_key VARCHAR(160);

ALTER TABLE chat_session
    ADD CONSTRAINT ck_chat_session_uuid_id
        CHECK (id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    ADD CONSTRAINT ck_chat_session_visibility_scope
        CHECK (visibility_scope IN ('USER', 'COMPANY')),
    ADD CONSTRAINT ck_chat_session_web_has_no_source_key
        CHECK (channel_code <> 'web' OR source_key IS NULL),
    ADD CONSTRAINT uk_chat_session_id_company UNIQUE (id, company_id);

CREATE UNIQUE INDEX uk_chat_session_company_channel_source
    ON chat_session(company_id, channel_code, source_key)
    WHERE source_key IS NOT NULL;

CREATE INDEX idx_chat_session_company_scope_updated
    ON chat_session(company_id, visibility_scope, updated_at DESC);

ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_session
        FOREIGN KEY (session_id, company_id) REFERENCES chat_session(id, company_id) ON DELETE CASCADE;

ALTER TABLE chat_session_state
    ADD CONSTRAINT fk_chat_session_state_session
        FOREIGN KEY (session_id, company_id) REFERENCES chat_session(id, company_id) ON DELETE CASCADE;

ALTER TABLE chat_attachment
    ADD CONSTRAINT fk_chat_attachment_session
        FOREIGN KEY (session_id, company_id) REFERENCES chat_session(id, company_id) ON DELETE CASCADE;
