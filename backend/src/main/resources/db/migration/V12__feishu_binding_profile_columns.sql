ALTER TABLE feishu_bot_binding
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(256);

ALTER TABLE feishu_bot_binding
    ADD COLUMN IF NOT EXISTS avatar_url TEXT;

CREATE INDEX IF NOT EXISTS idx_feishu_bot_binding_org_chat_status
    ON feishu_bot_binding(org_id, chat_id, status);
