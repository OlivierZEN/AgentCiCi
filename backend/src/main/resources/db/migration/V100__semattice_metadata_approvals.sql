CREATE TABLE semattice_metadata_approval (
    approval_id VARCHAR(64) PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(64) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    requester_member_id VARCHAR(64) NOT NULL REFERENCES company_member(id) ON DELETE RESTRICT,
    approver_member_id VARCHAR(64) REFERENCES company_member(id) ON DELETE RESTRICT,
    state VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_semattice_metadata_approval_subject_type CHECK (subject_type IN ('METADATA_VERSION', 'CHANGESET')),
    CONSTRAINT chk_semattice_metadata_approval_state CHECK (state IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_semattice_metadata_approval_distinct_actors CHECK (approver_member_id IS NULL OR approver_member_id <> requester_member_id)
);

CREATE INDEX idx_semattice_metadata_approval_requester_state
    ON semattice_metadata_approval(company_id, requester_member_id, state, expires_at);

CREATE INDEX idx_semattice_metadata_approval_subject
    ON semattice_metadata_approval(company_id, subject_type, subject_id, state);
