ALTER TABLE wecom_kf_account
    ADD COLUMN mobile_entry_id UUID,
    ADD COLUMN wecom_app_agent_id VARCHAR(64),
    ADD COLUMN wecom_app_secret_cipher TEXT,
    ADD COLUMN wecom_app_secret_iv VARCHAR(64),
    ADD COLUMN wecom_app_access_token_cipher TEXT,
    ADD COLUMN wecom_app_access_token_iv VARCHAR(64),
    ADD COLUMN wecom_app_access_token_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN mobile_handoff_enabled BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE wecom_kf_account
SET mobile_entry_id = gen_random_uuid()
WHERE mobile_entry_id IS NULL;

ALTER TABLE wecom_kf_account
    ALTER COLUMN mobile_entry_id SET NOT NULL,
    ADD CONSTRAINT uk_wecom_kf_account_mobile_entry UNIQUE (mobile_entry_id),
    ADD CONSTRAINT ck_wecom_kf_mobile_agent_required
        CHECK (mobile_handoff_enabled = FALSE OR (
            NULLIF(BTRIM(wecom_app_agent_id), '') IS NOT NULL
            AND NULLIF(BTRIM(wecom_app_secret_cipher), '') IS NOT NULL
            AND NULLIF(BTRIM(wecom_app_secret_iv), '') IS NOT NULL
        ));

ALTER TABLE wecom_kf_conversation
    ADD COLUMN public_id UUID,
    ADD COLUMN remote_service_state INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN owner_mode VARCHAR(16) NOT NULL DEFAULT 'AI',
    ADD COLUMN servicer_userid VARCHAR(128),
    ADD COLUMN state_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN state_checked_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN handoff_reason VARCHAR(64);

UPDATE wecom_kf_conversation
SET public_id = gen_random_uuid()
WHERE public_id IS NULL;

ALTER TABLE wecom_kf_conversation
    ALTER COLUMN public_id SET NOT NULL,
    ADD CONSTRAINT uk_wecom_kf_conversation_public_id UNIQUE (public_id),
    ADD CONSTRAINT ck_wecom_kf_remote_service_state CHECK (remote_service_state BETWEEN 0 AND 4),
    ADD CONSTRAINT ck_wecom_kf_owner_mode CHECK (owner_mode IN ('AI', 'HANDOFF', 'PENDING', 'HUMAN', 'ENDED'));

CREATE INDEX idx_wecom_kf_conversation_mobile_queue
    ON wecom_kf_conversation(company_id, open_kfid, owner_mode, updated_at DESC);

ALTER TABLE wecom_kf_message
    ADD COLUMN origin INTEGER,
    ADD COLUMN servicer_userid VARCHAR(128),
    ADD COLUMN event_type VARCHAR(64),
    ADD COLUMN remote_msg_id VARCHAR(128);

CREATE TABLE wecom_kf_handoff_operation (
    id BIGSERIAL PRIMARY KEY,
    operation_id UUID NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    conversation_id BIGINT NOT NULL REFERENCES wecom_kf_conversation(id) ON DELETE CASCADE,
    actor_userid VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    expected_revision BIGINT NOT NULL,
    resulting_revision BIGINT,
    old_state INTEGER NOT NULL,
    target_state INTEGER NOT NULL,
    readback_state INTEGER,
    status VARCHAR(24) NOT NULL,
    reason VARCHAR(64),
    error_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_wecom_kf_handoff_operation_id UNIQUE (operation_id),
    CONSTRAINT uk_wecom_kf_handoff_idempotency UNIQUE (company_id, conversation_id, actor_userid, idempotency_key),
    CONSTRAINT ck_wecom_kf_handoff_state CHECK (old_state BETWEEN 0 AND 4 AND target_state BETWEEN 0 AND 4),
    CONSTRAINT ck_wecom_kf_handoff_status CHECK (status IN ('IN_PROGRESS', 'SUCCEEDED', 'FAILED', 'CONFLICT'))
);

CREATE INDEX idx_wecom_kf_handoff_conversation_created
    ON wecom_kf_handoff_operation(company_id, conversation_id, created_at DESC);
