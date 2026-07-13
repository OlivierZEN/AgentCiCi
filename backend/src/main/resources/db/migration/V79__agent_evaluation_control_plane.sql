ALTER TABLE agent_eval_suite
    ADD COLUMN IF NOT EXISTS scope_type VARCHAR(32) NOT NULL DEFAULT 'TENANT_PRIVATE',
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(32) NOT NULL DEFAULT 'TENANT_ONLY',
    ADD COLUMN IF NOT EXISTS release_status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN IF NOT EXISTS template_code VARCHAR(128),
    ADD COLUMN IF NOT EXISTS version_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS app_code VARCHAR(128),
    ADD COLUMN IF NOT EXISTS industry_code VARCHAR(128),
    ADD COLUMN IF NOT EXISTS hidden_results BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_agent_eval_suite_scope_release
    ON agent_eval_suite(scope_type, release_status, status);

CREATE INDEX IF NOT EXISTS idx_agent_eval_suite_template_version
    ON agent_eval_suite(template_code, version_no);

ALTER TABLE agent_eval_case
    ADD COLUMN IF NOT EXISTS case_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS category VARCHAR(64) NOT NULL DEFAULT 'ANSWER_QUALITY',
    ADD COLUMN IF NOT EXISTS conversation_history_json TEXT,
    ADD COLUMN IF NOT EXISTS fixture_json TEXT,
    ADD COLUMN IF NOT EXISTS assertion_config_json TEXT,
    ADD COLUMN IF NOT EXISTS judge_config_json TEXT,
    ADD COLUMN IF NOT EXISTS tags_json TEXT,
    ADD COLUMN IF NOT EXISTS created_from_trace_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS hidden_case BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS redaction_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED';

CREATE INDEX IF NOT EXISTS idx_agent_eval_case_trace
    ON agent_eval_case(org_id, created_from_trace_id);

ALTER TABLE agent_eval_run
    ADD COLUMN IF NOT EXISTS target_type VARCHAR(32) NOT NULL DEFAULT 'CANDIDATE',
    ADD COLUMN IF NOT EXISTS baseline_version_no INTEGER,
    ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS runtime_snapshot_json TEXT,
    ADD COLUMN IF NOT EXISTS snapshot_fingerprint VARCHAR(128),
    ADD COLUMN IF NOT EXISTS avg_latency_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_elapsed_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tool_call_accuracy DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rag_hit_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_agent_eval_run_snapshot
    ON agent_eval_run(org_id, agent_id, snapshot_fingerprint);

ALTER TABLE agent_eval_case_result
    ADD COLUMN IF NOT EXISTS failure_category VARCHAR(64),
    ADD COLUMN IF NOT EXISTS failure_summary VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS assertion_results_json TEXT,
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS score DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS elapsed_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tool_call_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rag_hit_count INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS agent_eval_suite_binding (
    id BIGSERIAL PRIMARY KEY,
    suite_id BIGINT NOT NULL,
    org_id VARCHAR(64),
    agent_id VARCHAR(64),
    app_code VARCHAR(128),
    industry_code VARCHAR(128),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_agent_eval_binding_resolution
    ON agent_eval_suite_binding(org_id, agent_id, app_code, industry_code, enabled);

CREATE TABLE IF NOT EXISTS agent_eval_issue (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    run_id BIGINT,
    case_id BIGINT,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    root_cause_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    owner_user_id VARCHAR(128),
    fix_version_no INTEGER,
    verification_run_id BIGINT,
    description TEXT,
    resolution TEXT,
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_eval_issue_org_agent
    ON agent_eval_issue(org_id, agent_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS agent_eval_publish_reference (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    version_no INTEGER NOT NULL,
    eval_run_ids_json TEXT NOT NULL,
    snapshot_fingerprint VARCHAR(128),
    published_by VARCHAR(128),
    published_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_eval_publish_ref_agent_version
    ON agent_eval_publish_reference(org_id, agent_id, version_no, published_at DESC);

INSERT INTO agent_eval_suite (
    org_id, agent_id, name, description, status, gate_mode, min_pass_rate,
    created_at, updated_at, scope_type, visibility, release_status,
    template_code, version_no, hidden_results, mandatory, created_by
)
SELECT
    '__platform__', '*', '平台安全核心评测集',
    '平台维护的安全、权限和高风险动作基线。发布前需由平台运营审核并发布。',
    'ACTIVE', 'BLOCKING', 1.0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'PLATFORM_CORE', 'SEALED', 'DRAFT',
    'platform-safety-core', 1, TRUE, TRUE, 'system'
WHERE NOT EXISTS (
    SELECT 1 FROM agent_eval_suite
    WHERE scope_type = 'PLATFORM_CORE' AND template_code = 'platform-safety-core' AND version_no = 1
);
