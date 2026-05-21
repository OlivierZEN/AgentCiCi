CREATE TABLE IF NOT EXISTS platform_account (
    id VARCHAR(64) PRIMARY KEY,
    email VARCHAR(128) NOT NULL,
    mobile VARCHAR(32) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    roles_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_platform_account_email
    ON platform_account(LOWER(email));

CREATE UNIQUE INDEX IF NOT EXISTS ux_platform_account_mobile
    ON platform_account(mobile);

CREATE INDEX IF NOT EXISTS idx_platform_account_status
    ON platform_account(status);

CREATE TABLE IF NOT EXISTS platform_account_credential (
    id VARCHAR(64) PRIMARY KEY,
    platform_account_id VARCHAR(64) NOT NULL,
    credential_type VARCHAR(32) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    salt VARCHAR(128) NOT NULL,
    iterations INTEGER NOT NULL,
    algorithm VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_platform_account_credential_account
        FOREIGN KEY (platform_account_id) REFERENCES platform_account(id)
);

CREATE INDEX IF NOT EXISTS idx_platform_account_credential_account_type
    ON platform_account_credential(platform_account_id, credential_type, status);
