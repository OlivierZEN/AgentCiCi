ALTER TABLE user_account ADD COLUMN IF NOT EXISTS first_name VARCHAR(64);
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS last_name VARCHAR(64);
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS email VARCHAR(128);

CREATE TABLE IF NOT EXISTS account_auth_credential (
    id VARCHAR(64) PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL,
    credential_type VARCHAR(32) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    salt VARCHAR(128) NOT NULL,
    iterations INTEGER NOT NULL,
    algorithm VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_account_auth_credential_account FOREIGN KEY (account_id) REFERENCES user_account(id)
);

CREATE INDEX IF NOT EXISTS idx_account_auth_credential_account_type
    ON account_auth_credential(account_id, credential_type, status);
