ALTER TABLE agent_runtime_schedule_trigger
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE agent_runtime_schedule_trigger
SET enabled = TRUE
WHERE enabled IS NULL;
