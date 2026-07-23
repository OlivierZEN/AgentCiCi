CREATE TABLE IF NOT EXISTS agent_task_review (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    run_id BIGINT NOT NULL REFERENCES agent_task_run(id) ON DELETE CASCADE,
    review_round INTEGER NOT NULL,
    gate_status VARCHAR(24) NOT NULL,
    reviewer_status VARCHAR(24) NOT NULL,
    issue_codes_json TEXT NOT NULL,
    result_summary VARCHAR(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (org_id, run_id, review_round)
);

CREATE INDEX IF NOT EXISTS idx_agent_task_review_org_run_round
    ON agent_task_review(org_id, run_id, review_round);
