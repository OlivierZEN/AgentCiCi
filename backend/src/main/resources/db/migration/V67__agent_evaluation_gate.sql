CREATE TABLE IF NOT EXISTS agent_eval_suite (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    gate_mode VARCHAR(32) NOT NULL,
    min_pass_rate DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_eval_suite_org_agent
    ON agent_eval_suite(org_id, agent_id, status);

CREATE TABLE IF NOT EXISTS agent_eval_case (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    suite_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    input_text TEXT NOT NULL,
    assertion_type VARCHAR(64) NOT NULL,
    expected_text VARCHAR(1000),
    forbidden_text VARCHAR(1000),
    expected_status VARCHAR(64),
    required_tool_name VARCHAR(128),
    forbidden_tool_name VARCHAR(128),
    priority VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_eval_case_org_suite
    ON agent_eval_case(org_id, suite_id, status);

CREATE TABLE IF NOT EXISTS agent_eval_run (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    suite_id BIGINT NOT NULL,
    version_no INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    case_count INTEGER NOT NULL,
    passed_count INTEGER NOT NULL,
    failed_count INTEGER NOT NULL,
    p0_failed_count INTEGER NOT NULL,
    safety_failed_count INTEGER NOT NULL,
    pass_rate DOUBLE PRECISION NOT NULL,
    summary_json TEXT,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_eval_run_org_agent_version
    ON agent_eval_run(org_id, agent_id, version_no, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_eval_run_org_suite
    ON agent_eval_run(org_id, suite_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_eval_case_result (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    run_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    version_no INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    assertion_type VARCHAR(64) NOT NULL,
    actual_status VARCHAR(64),
    output_preview TEXT,
    result_summary_json TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_eval_case_result_org_run
    ON agent_eval_case_result(org_id, run_id);
