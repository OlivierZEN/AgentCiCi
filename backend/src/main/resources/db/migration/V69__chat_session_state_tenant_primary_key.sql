ALTER TABLE chat_session_state
    DROP CONSTRAINT IF EXISTS chat_session_state_pkey;

ALTER TABLE chat_session_state
    ADD CONSTRAINT chat_session_state_pkey PRIMARY KEY (session_id, org_id);
