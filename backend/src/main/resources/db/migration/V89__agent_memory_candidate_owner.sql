ALTER TABLE memory_candidate ADD COLUMN IF NOT EXISTS agent_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_memory_candidate_agent_review
    ON memory_candidate(org_id, agent_id, status, updated_at DESC);
