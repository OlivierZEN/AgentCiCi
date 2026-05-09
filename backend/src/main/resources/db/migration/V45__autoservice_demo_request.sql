CREATE TABLE IF NOT EXISTS autoservice_demo_request (
    id BIGSERIAL PRIMARY KEY,
    site VARCHAR(32) NOT NULL,
    locale VARCHAR(16) NOT NULL,
    company_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64) NOT NULL,
    mobile VARCHAR(64) NOT NULL,
    email VARCHAR(128),
    role_title VARCHAR(128),
    scenario TEXT,
    source_path VARCHAR(256),
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    handled_by VARCHAR(64),
    handled_note TEXT,
    handled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_autoservice_demo_request_status_created
    ON autoservice_demo_request(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_autoservice_demo_request_created
    ON autoservice_demo_request(created_at DESC);
