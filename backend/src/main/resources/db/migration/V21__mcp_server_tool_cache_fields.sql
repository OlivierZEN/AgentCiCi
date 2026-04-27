ALTER TABLE mcp_server ADD COLUMN IF NOT EXISTS tool_cache_json TEXT;
ALTER TABLE mcp_server ADD COLUMN IF NOT EXISTS tool_cache_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE mcp_server ADD COLUMN IF NOT EXISTS tool_cache_status VARCHAR(32) NOT NULL DEFAULT 'empty';
ALTER TABLE mcp_server ADD COLUMN IF NOT EXISTS tool_cache_updated_at TIMESTAMP;
ALTER TABLE mcp_server ADD COLUMN IF NOT EXISTS tool_cache_error_message TEXT;
ALTER TABLE mcp_server ADD COLUMN IF NOT EXISTS tool_cache_last_attempt_at TIMESTAMP;
ALTER TABLE mcp_server ADD COLUMN IF NOT EXISTS tool_cache_version VARCHAR(64);
