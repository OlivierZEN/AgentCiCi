CREATE TABLE IF NOT EXISTS org (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_account (
    id VARCHAR(64) PRIMARY KEY,
    primary_mobile VARCHAR(32) NOT NULL,
    display_name VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_account_primary_mobile
    ON user_account(primary_mobile);

CREATE TABLE IF NOT EXISTS account_login_identifier (
    id VARCHAR(64) PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL,
    identifier_type VARCHAR(32) NOT NULL,
    normalized_value VARCHAR(256) NOT NULL,
    display_value VARCHAR(256) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_account_login_identifier_account FOREIGN KEY (account_id) REFERENCES user_account(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_account_login_identifier_active_value
    ON account_login_identifier(identifier_type, normalized_value, status);

CREATE TABLE IF NOT EXISTS organization_member (
    id VARCHAR(64) PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    member_status VARCHAR(32) NOT NULL,
    nickname VARCHAR(128),
    cc_username VARCHAR(128),
    cc_safetymark VARCHAR(128),
    avatar_base64 TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_organization_member_org FOREIGN KEY (org_id) REFERENCES org(id),
    CONSTRAINT fk_organization_member_account FOREIGN KEY (account_id) REFERENCES user_account(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_organization_member_org_account
    ON organization_member(org_id, account_id);

CREATE INDEX IF NOT EXISTS idx_organization_member_org_created
    ON organization_member(org_id, created_at DESC);
