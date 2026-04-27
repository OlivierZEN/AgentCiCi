CREATE TABLE IF NOT EXISTS feishu_bot_binding (
    id              BIGSERIAL    PRIMARY KEY,
    org_id          VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    tenant_key      VARCHAR(128) NOT NULL,
    open_id         VARCHAR(128) NOT NULL,
    union_id        VARCHAR(128),
    chat_id         VARCHAR(128),
    agent_code      VARCHAR(64)  NOT NULL DEFAULT 'cici',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    paired_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_message_at TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_feishu_bot_binding_org_open UNIQUE (org_id, tenant_key, open_id)
);

CREATE INDEX IF NOT EXISTS idx_feishu_bot_binding_org_user
    ON feishu_bot_binding(org_id, user_id);

CREATE INDEX IF NOT EXISTS idx_feishu_bot_binding_org_status
    ON feishu_bot_binding(org_id, status);
