CREATE TABLE IF NOT EXISTS wecom_kf_account (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    corp_id VARCHAR(64) NOT NULL,
    open_kfid VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    secret_cipher TEXT NOT NULL,
    secret_iv VARCHAR(64) NOT NULL,
    token VARCHAR(128) NOT NULL,
    encoding_aes_key_cipher TEXT NOT NULL,
    encoding_aes_key_iv VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    run_as_user_id VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sync_cursor TEXT,
    access_token_cipher TEXT,
    access_token_iv VARCHAR(64),
    access_token_expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wecom_kf_account_org_open_kf
    ON wecom_kf_account(org_id, open_kfid);

CREATE INDEX IF NOT EXISTS idx_wecom_kf_account_enabled
    ON wecom_kf_account(enabled, org_id, corp_id);

CREATE TABLE IF NOT EXISTS wecom_kf_conversation (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    corp_id VARCHAR(64) NOT NULL,
    open_kfid VARCHAR(128) NOT NULL,
    external_userid VARCHAR(128) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    run_as_user_id VARCHAR(64) NOT NULL,
    last_customer_message_at TIMESTAMP,
    reply_count_in_window INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wecom_kf_conversation_customer
    ON wecom_kf_conversation(org_id, corp_id, open_kfid, external_userid);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wecom_kf_conversation_session
    ON wecom_kf_conversation(session_id);

CREATE TABLE IF NOT EXISTS wecom_kf_message (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    msg_id VARCHAR(128) NOT NULL,
    corp_id VARCHAR(64) NOT NULL,
    open_kfid VARCHAR(128) NOT NULL,
    external_userid VARCHAR(128),
    direction VARCHAR(16) NOT NULL,
    msg_type VARCHAR(32) NOT NULL,
    content_summary TEXT,
    trace_id VARCHAR(64),
    send_status VARCHAR(32),
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wecom_kf_message_org_msg
    ON wecom_kf_message(org_id, msg_id);

CREATE INDEX IF NOT EXISTS idx_wecom_kf_message_customer_created
    ON wecom_kf_message(org_id, corp_id, open_kfid, external_userid, created_at DESC);
