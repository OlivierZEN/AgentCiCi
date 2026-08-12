CREATE TABLE semattice_console_handoff (
    ticket_digest CHAR(64) PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    company_member_id VARCHAR(64) NOT NULL REFERENCES company_member(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_semattice_console_handoff_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_semattice_console_handoff_active
    ON semattice_console_handoff(expires_at)
    WHERE consumed_at IS NULL;
