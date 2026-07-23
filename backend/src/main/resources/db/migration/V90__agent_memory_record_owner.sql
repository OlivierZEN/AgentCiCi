ALTER TABLE memory_record ADD COLUMN IF NOT EXISTS agent_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_memory_record_agent_lookup
    ON memory_record(org_id, agent_id, status, updated_at DESC);
