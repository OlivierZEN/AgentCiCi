CREATE TABLE IF NOT EXISTS customer_insight_project (
    id BIGSERIAL PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL UNIQUE,
    org_id VARCHAR(64) NOT NULL,
    owner_user_id VARCHAR(64) NOT NULL,
    customer_name VARCHAR(256) NOT NULL,
    customer_external_id VARCHAR(128),
    customer_object_api_name VARCHAR(128),
    industry VARCHAR(128),
    source_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    completeness_score INTEGER NOT NULL DEFAULT 0,
    latest_summary TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_insight_project_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE INDEX IF NOT EXISTS idx_customer_insight_project_org_updated
    ON customer_insight_project(org_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS customer_insight_section (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    section_code VARCHAR(64) NOT NULL,
    section_group VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    input_json TEXT NOT NULL,
    output_json TEXT NOT NULL,
    markdown TEXT,
    status VARCHAR(32) NOT NULL,
    ai_generated BOOLEAN NOT NULL DEFAULT FALSE,
    model_provider VARCHAR(64),
    model_name VARCHAR(128),
    skill_code VARCHAR(64),
    trace_id VARCHAR(64),
    error_message VARCHAR(1000),
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_insight_section_project FOREIGN KEY (project_id) REFERENCES customer_insight_project(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_insight_section_project_code
    ON customer_insight_section(project_id, section_code);

CREATE INDEX IF NOT EXISTS idx_customer_insight_section_project_group
    ON customer_insight_section(project_id, section_group, section_code);

CREATE TABLE IF NOT EXISTS customer_insight_source_snapshot (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_key VARCHAR(256) NOT NULL,
    source_label VARCHAR(256) NOT NULL,
    snapshot_json TEXT NOT NULL,
    collected_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_insight_source_project FOREIGN KEY (project_id) REFERENCES customer_insight_project(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_customer_insight_source_project
    ON customer_insight_source_snapshot(project_id, collected_at DESC);

CREATE TABLE IF NOT EXISTS customer_insight_generation_job (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    section_code VARCHAR(64),
    job_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_summary TEXT NOT NULL,
    result_summary TEXT,
    trace_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT fk_customer_insight_job_project FOREIGN KEY (project_id) REFERENCES customer_insight_project(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_customer_insight_job_project_created
    ON customer_insight_generation_job(project_id, created_at DESC);
