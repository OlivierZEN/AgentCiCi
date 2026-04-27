CREATE TABLE IF NOT EXISTS mcp_server (
    id          BIGSERIAL    PRIMARY KEY,
    org_id      VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    transport_type VARCHAR(32) NOT NULL DEFAULT 'streamableHttp',
    url         VARCHAR(512) NOT NULL,
    headers     TEXT,
    timeout_seconds INTEGER  NOT NULL DEFAULT 60,
    enabled     BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mcp_server_org ON mcp_server(org_id);
