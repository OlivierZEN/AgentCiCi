ALTER TABLE agent_workflow_version
    ADD COLUMN IF NOT EXISTS compile_fingerprint VARCHAR(128);

ALTER TABLE agent_workflow_version
    ADD COLUMN IF NOT EXISTS change_log TEXT;
