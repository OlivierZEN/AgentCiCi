-- 用户专属记忆表
-- 每条记忆归属于 (org_id, user_id, agent_id) 三元组，跨会话持久存储。
--
-- 后端集成测试统一使用 PostgreSQL；本迁移保留标准 PostgreSQL 可执行语法。
CREATE TABLE user_memory (
    id          BIGSERIAL    PRIMARY KEY,
    org_id      VARCHAR(64)  NOT NULL,
    user_id     VARCHAR(64)  NOT NULL,
    agent_id    VARCHAR(64)  NOT NULL DEFAULT 'cici-system',
    category    VARCHAR(32)  NOT NULL,  -- FACT | PREFERENCE | CONTEXT | INSTRUCTION
    source      VARCHAR(32)  NOT NULL DEFAULT 'MANUAL',  -- MANUAL | EXTRACTED
    content     TEXT         NOT NULL,
    memory_key  VARCHAR(128),           -- 可选语义键，用于按 key upsert（如 user.role）
    confidence  DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    pinned      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

-- 主要查询索引：按用户 + agent 获取有效记忆
CREATE INDEX idx_user_memory_lookup
    ON user_memory(org_id, user_id, agent_id, enabled);

-- 语义键唯一索引：memory_key 可为 NULL，无 key 的记忆允许同一 (org, user, agent) 下多条并存
-- PostgreSQL UNIQUE 索引会将 NULL 视为彼此不相等；
-- 带 key 的记忆则按 (org_id, user_id, agent_id, memory_key) 唯一，支撑按 key upsert。
CREATE UNIQUE INDEX idx_user_memory_key
    ON user_memory(org_id, user_id, agent_id, memory_key);
