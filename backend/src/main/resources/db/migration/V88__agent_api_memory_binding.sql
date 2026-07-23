CREATE TABLE IF NOT EXISTS agent_api_memory_binding (
    id BIGSERIAL PRIMARY KEY,
    credential_id BIGINT NOT NULL UNIQUE,
    application_code VARCHAR(96) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    identity_level VARCHAR(32) NOT NULL,
    domain_namespaces_json TEXT NOT NULL DEFAULT '[]',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_agent_api_memory_binding_credential FOREIGN KEY (credential_id) REFERENCES agent_api_credential(id)
);
