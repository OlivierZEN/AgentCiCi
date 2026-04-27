CREATE TABLE IF NOT EXISTS model_provider_config (
    id            BIGSERIAL    PRIMARY KEY,
    org_id        VARCHAR(64)  NOT NULL,
    provider_code VARCHAR(64)  NOT NULL,
    provider_name VARCHAR(128) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT true,
    api_base_url  VARCHAR(512) NOT NULL,
    api_key       VARCHAR(512) NOT NULL DEFAULT '',
    config_json   TEXT         NOT NULL DEFAULT '{}',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_model_provider_org_code UNIQUE (org_id, provider_code)
);

CREATE INDEX IF NOT EXISTS idx_model_provider_org ON model_provider_config(org_id);
