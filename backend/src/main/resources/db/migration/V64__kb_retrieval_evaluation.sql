CREATE TABLE IF NOT EXISTS kb_eval_suite (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_eval_suite_org_kb
    ON kb_eval_suite(org_id, knowledge_base_id, status);

CREATE TABLE IF NOT EXISTS kb_eval_case (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    suite_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    query TEXT NOT NULL,
    expected_document_id BIGINT,
    expected_document_keyword VARCHAR(256),
    expected_chunk_keyword VARCHAR(512),
    min_score DOUBLE PRECISION,
    forbidden_document_id BIGINT,
    metadata_filters_json TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_eval_case_org_suite
    ON kb_eval_case(org_id, suite_id, status);

CREATE TABLE IF NOT EXISTS kb_eval_run (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    suite_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    case_count INTEGER NOT NULL,
    passed_count INTEGER NOT NULL,
    failed_count INTEGER NOT NULL,
    hit_rate DOUBLE PRECISION NOT NULL,
    expected_source_recall DOUBLE PRECISION NOT NULL,
    forbidden_source_violations INTEGER NOT NULL,
    average_top_score DOUBLE PRECISION NOT NULL,
    stale_source_rate DOUBLE PRECISION NOT NULL,
    summary_json TEXT,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_eval_run_org_suite
    ON kb_eval_run(org_id, suite_id, created_at DESC);

CREATE TABLE IF NOT EXISTS kb_eval_case_result (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    run_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    expected_hit BOOLEAN NOT NULL,
    forbidden_violation BOOLEAN NOT NULL,
    stale_source BOOLEAN NOT NULL,
    top_score DOUBLE PRECISION NOT NULL,
    matched_document_id BIGINT,
    matched_chunk_id BIGINT,
    result_summary_json TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_kb_eval_case_result_org_run
    ON kb_eval_case_result(org_id, run_id);
