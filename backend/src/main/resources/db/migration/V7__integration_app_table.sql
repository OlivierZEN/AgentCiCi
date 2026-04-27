CREATE TABLE IF NOT EXISTS integration_app (
    id            BIGSERIAL    PRIMARY KEY,
    org_id        VARCHAR(64)  NOT NULL,
    app_code      VARCHAR(64)  NOT NULL,
    app_name      VARCHAR(128) NOT NULL,
    description   TEXT,
    enabled       BOOLEAN      NOT NULL DEFAULT true,
    config_json   TEXT         NOT NULL DEFAULT '{}',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_integration_app_org_code UNIQUE (org_id, app_code)
);

CREATE INDEX IF NOT EXISTS idx_integration_app_org ON integration_app(org_id);
