CREATE TABLE IF NOT EXISTS email_account (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    provider_code VARCHAR(32) NOT NULL,
    display_name VARCHAR(128),
    email_address VARCHAR(256) NOT NULL,
    login_username VARCHAR(256) NOT NULL,
    auth_type VARCHAR(16) NOT NULL,
    secret_cipher TEXT NOT NULL,
    secret_iv VARCHAR(64) NOT NULL,
    pop3_host VARCHAR(128) NOT NULL,
    pop3_port INTEGER NOT NULL,
    pop3_ssl BOOLEAN NOT NULL DEFAULT TRUE,
    smtp_host VARCHAR(128) NOT NULL,
    smtp_port INTEGER NOT NULL,
    smtp_ssl_mode VARCHAR(16) NOT NULL,
    require_send_confirm BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_verified_at TIMESTAMP,
    last_verify_error VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_email_account_org_user_address
    ON email_account(org_id, user_id, email_address);

CREATE INDEX IF NOT EXISTS idx_email_account_org_user
    ON email_account(org_id, user_id);
