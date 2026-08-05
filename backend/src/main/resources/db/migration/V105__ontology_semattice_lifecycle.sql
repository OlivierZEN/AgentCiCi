CREATE TABLE ontology_semattice_binding (
    id BIGSERIAL PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    workspace_id BIGINT NOT NULL REFERENCES ontology_workspace(id) ON DELETE CASCADE,
    semattice_tenant_id VARCHAR(64) NOT NULL,
    active_metadata_version_id VARCHAR(64),
    active_sequence BIGINT,
    active_digest VARCHAR(128),
    sync_status VARCHAR(32) NOT NULL,
    last_error_code VARCHAR(64),
    last_checked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_ontology_semattice_binding_workspace UNIQUE (workspace_id),
    CONSTRAINT chk_ontology_semattice_binding_status CHECK (
        sync_status IN ('LINKED', 'IN_SYNC', 'DRIFTED', 'PUBLISHING', 'FAILED'))
);

CREATE INDEX idx_ontology_semattice_binding_company_status
    ON ontology_semattice_binding(company_id, sync_status, updated_at DESC);

CREATE TABLE ontology_semattice_element_binding (
    id BIGSERIAL PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    workspace_id BIGINT NOT NULL REFERENCES ontology_workspace(id) ON DELETE CASCADE,
    element_type VARCHAR(32) NOT NULL,
    element_key VARCHAR(256) NOT NULL,
    semattice_element_id VARCHAR(64) NOT NULL,
    semattice_api_name VARCHAR(96) NOT NULL,
    first_bound_revision BIGINT NOT NULL,
    last_synced_revision BIGINT NOT NULL,
    source_digest VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_ontology_semattice_element_key UNIQUE (workspace_id, element_type, element_key),
    CONSTRAINT uq_ontology_semattice_element_id UNIQUE (workspace_id, semattice_element_id),
    CONSTRAINT chk_ontology_semattice_element_type CHECK (
        element_type IN ('CONCEPT', 'PROPERTY', 'RELATION')),
    CONSTRAINT chk_ontology_semattice_element_status CHECK (
        status IN ('ACTIVE', 'DRIFTED', 'RETIRED'))
);

CREATE INDEX idx_ontology_semattice_element_workspace
    ON ontology_semattice_element_binding(company_id, workspace_id, element_type, status);

CREATE TABLE ontology_semattice_operation (
    operation_id VARCHAR(64) PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    workspace_id BIGINT NOT NULL REFERENCES ontology_workspace(id) ON DELETE CASCADE,
    operation_type VARCHAR(32) NOT NULL,
    source_revision BIGINT NOT NULL,
    source_digest VARCHAR(128) NOT NULL,
    base_metadata_version_id VARCHAR(64),
    candidate_metadata_version_id VARCHAR(64),
    changeset_id VARCHAR(64),
    subject_type VARCHAR(32),
    subject_id VARCHAR(64),
    approval_request_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    risk_level VARCHAR(32),
    requires_backfill BOOLEAN NOT NULL DEFAULT FALSE,
    requested_by VARCHAR(64) NOT NULL REFERENCES company_member(id) ON DELETE RESTRICT,
    approved_by VARCHAR(64) REFERENCES company_member(id) ON DELETE RESTRICT,
    last_error_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    activated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_ontology_semattice_operation_revision UNIQUE (
        workspace_id, operation_type, source_revision, source_digest),
    CONSTRAINT chk_ontology_semattice_operation_type CHECK (
        operation_type IN ('INITIAL_PUBLISH', 'CHANGESET', 'ROLLBACK')),
    CONSTRAINT chk_ontology_semattice_operation_status CHECK (
        status IN ('COMPILING', 'VALIDATED', 'APPROVAL_PENDING', 'APPROVED',
                   'BACKFILLING', 'READY', 'ACTIVE', 'FAILED', 'CANCELED', 'ROLLED_BACK')),
    CONSTRAINT chk_ontology_semattice_operation_subject CHECK (
        (subject_type IS NULL AND subject_id IS NULL)
        OR (subject_type IN ('METADATA_VERSION', 'CHANGESET') AND subject_id IS NOT NULL))
);

CREATE INDEX idx_ontology_semattice_operation_workspace_status
    ON ontology_semattice_operation(company_id, workspace_id, status, updated_at DESC);
