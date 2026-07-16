CREATE TABLE ontology_workspace (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    key VARCHAR(128) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    draft_revision BIGINT NOT NULL DEFAULT 0,
    published_version INTEGER,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ontology_workspace_id_org UNIQUE (id, org_id),
    CONSTRAINT uq_ontology_workspace_org_key UNIQUE (org_id, key)
);

CREATE TABLE ontology_concept (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    workspace_id BIGINT NOT NULL,
    key VARCHAR(128) NOT NULL,
    name VARCHAR(160) NOT NULL,
    plural_name VARCHAR(160),
    description TEXT,
    concept_type VARCHAR(32) NOT NULL,
    display_property_key VARCHAR(128),
    position_x DOUBLE PRECISION NOT NULL DEFAULT 0,
    position_y DOUBLE PRECISION NOT NULL DEFAULT 0,
    queryable BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ontology_concept_id_scope UNIQUE (id, workspace_id, org_id),
    CONSTRAINT fk_ontology_concept_workspace
        FOREIGN KEY (workspace_id, org_id)
        REFERENCES ontology_workspace(id, org_id) ON DELETE CASCADE,
    CONSTRAINT uq_ontology_concept_workspace_key UNIQUE (workspace_id, key)
);

CREATE TABLE ontology_property (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    workspace_id BIGINT NOT NULL,
    concept_id BIGINT NOT NULL,
    key VARCHAR(128) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    data_type VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    multiple BOOLEAN NOT NULL DEFAULT FALSE,
    sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    queryable BOOLEAN NOT NULL DEFAULT TRUE,
    enum_values_json TEXT,
    format_hint VARCHAR(128),
    display_strategy VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ontology_property_workspace
        FOREIGN KEY (workspace_id, org_id)
        REFERENCES ontology_workspace(id, org_id) ON DELETE CASCADE,
    CONSTRAINT fk_ontology_property_concept
        FOREIGN KEY (concept_id, workspace_id, org_id)
        REFERENCES ontology_concept(id, workspace_id, org_id) ON DELETE CASCADE,
    CONSTRAINT uq_ontology_property_concept_key UNIQUE (concept_id, key)
);

CREATE TABLE ontology_relation (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    workspace_id BIGINT NOT NULL,
    key VARCHAR(128) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    source_concept_id BIGINT NOT NULL,
    target_concept_id BIGINT NOT NULL,
    cardinality VARCHAR(32) NOT NULL,
    forward_label VARCHAR(160),
    reverse_label VARCHAR(160),
    queryable BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ontology_relation_workspace
        FOREIGN KEY (workspace_id, org_id)
        REFERENCES ontology_workspace(id, org_id) ON DELETE CASCADE,
    CONSTRAINT fk_ontology_relation_source
        FOREIGN KEY (source_concept_id, workspace_id, org_id)
        REFERENCES ontology_concept(id, workspace_id, org_id) ON DELETE CASCADE,
    CONSTRAINT fk_ontology_relation_target
        FOREIGN KEY (target_concept_id, workspace_id, org_id)
        REFERENCES ontology_concept(id, workspace_id, org_id) ON DELETE CASCADE,
    CONSTRAINT uq_ontology_relation_workspace_key UNIQUE (workspace_id, key)
);

CREATE TABLE ontology_metric (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    workspace_id BIGINT NOT NULL,
    key VARCHAR(128) NOT NULL,
    name VARCHAR(160) NOT NULL,
    concept_id BIGINT NOT NULL,
    aggregation VARCHAR(32) NOT NULL,
    measure_property_key VARCHAR(128),
    group_by_property_keys_json TEXT,
    time_property_key VARCHAR(128),
    filters_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ontology_metric_workspace
        FOREIGN KEY (workspace_id, org_id)
        REFERENCES ontology_workspace(id, org_id) ON DELETE CASCADE,
    CONSTRAINT fk_ontology_metric_concept
        FOREIGN KEY (concept_id, workspace_id, org_id)
        REFERENCES ontology_concept(id, workspace_id, org_id) ON DELETE CASCADE,
    CONSTRAINT uq_ontology_metric_workspace_key UNIQUE (workspace_id, key)
);

CREATE TABLE ontology_action (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    workspace_id BIGINT NOT NULL,
    key VARCHAR(128) NOT NULL,
    name VARCHAR(160) NOT NULL,
    concept_id BIGINT NOT NULL,
    description TEXT,
    parameters_json TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ontology_action_workspace
        FOREIGN KEY (workspace_id, org_id)
        REFERENCES ontology_workspace(id, org_id) ON DELETE CASCADE,
    CONSTRAINT fk_ontology_action_concept
        FOREIGN KEY (concept_id, workspace_id, org_id)
        REFERENCES ontology_concept(id, workspace_id, org_id) ON DELETE CASCADE,
    CONSTRAINT uq_ontology_action_workspace_key UNIQUE (workspace_id, key)
);

CREATE TABLE ontology_data_source (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    workspace_id BIGINT NOT NULL,
    key VARCHAR(128) NOT NULL,
    name VARCHAR(160) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    config_json TEXT,
    sample_data_json TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    last_validated_at TIMESTAMPTZ,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ontology_source_id_scope UNIQUE (id, workspace_id, org_id),
    CONSTRAINT fk_ontology_source_workspace
        FOREIGN KEY (workspace_id, org_id)
        REFERENCES ontology_workspace(id, org_id) ON DELETE CASCADE,
    CONSTRAINT uq_ontology_source_workspace_key UNIQUE (workspace_id, key)
);

CREATE TABLE ontology_physical_object (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    workspace_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    object_key VARCHAR(256) NOT NULL,
    name VARCHAR(160) NOT NULL,
    object_type VARCHAR(64),
    metadata_json TEXT,
    discovered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ontology_object_id_scope UNIQUE (id, workspace_id, org_id),
    CONSTRAINT fk_ontology_object_workspace
        FOREIGN KEY (workspace_id, org_id)
        REFERENCES ontology_workspace(id, org_id) ON DELETE CASCADE,
    CONSTRAINT fk_ontology_object_source
        FOREIGN KEY (data_source_id, workspace_id, org_id)
        REFERENCES ontology_data_source(id, workspace_id, org_id) ON DELETE CASCADE,
    CONSTRAINT uq_ontology_object_source_key UNIQUE (data_source_id, object_key)
);

CREATE TABLE ontology_physical_field (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    workspace_id BIGINT NOT NULL,
    physical_object_id BIGINT NOT NULL,
    field_key VARCHAR(256) NOT NULL,
    name VARCHAR(160) NOT NULL,
    data_type VARCHAR(64) NOT NULL,
    nullable BOOLEAN NOT NULL DEFAULT TRUE,
    multiple BOOLEAN NOT NULL DEFAULT FALSE,
    metadata_json TEXT,
    discovered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ontology_field_workspace
        FOREIGN KEY (workspace_id, org_id)
        REFERENCES ontology_workspace(id, org_id) ON DELETE CASCADE,
    CONSTRAINT fk_ontology_field_object
        FOREIGN KEY (physical_object_id, workspace_id, org_id)
        REFERENCES ontology_physical_object(id, workspace_id, org_id) ON DELETE CASCADE,
    CONSTRAINT uq_ontology_field_object_key UNIQUE (physical_object_id, field_key)
);

CREATE TABLE ontology_mapping (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    workspace_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_key VARCHAR(256) NOT NULL,
    data_source_id BIGINT NOT NULL,
    physical_object_key VARCHAR(256) NOT NULL,
    physical_field_key VARCHAR(256),
    relation_target_field_key VARCHAR(256),
    transform TEXT,
    confidence NUMERIC(5, 4) NOT NULL DEFAULT 0,
    source VARCHAR(32) NOT NULL,
    validation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    last_validated_at TIMESTAMPTZ,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ontology_mapping_workspace
        FOREIGN KEY (workspace_id, org_id)
        REFERENCES ontology_workspace(id, org_id) ON DELETE CASCADE,
    CONSTRAINT fk_ontology_mapping_source
        FOREIGN KEY (data_source_id, workspace_id, org_id)
        REFERENCES ontology_data_source(id, workspace_id, org_id) ON DELETE CASCADE,
    CONSTRAINT uq_ontology_mapping_target_source
        UNIQUE (workspace_id, target_type, target_key, data_source_id)
);

CREATE TABLE ontology_ai_proposal (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    workspace_id BIGINT NOT NULL,
    proposal_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    instruction TEXT,
    payload_json TEXT NOT NULL,
    diff_json TEXT,
    validation_json TEXT,
    created_by VARCHAR(64) NOT NULL,
    applied_by VARCHAR(64),
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ontology_proposal_workspace
        FOREIGN KEY (workspace_id, org_id)
        REFERENCES ontology_workspace(id, org_id) ON DELETE CASCADE
);

CREATE TABLE ontology_version (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    workspace_id BIGINT NOT NULL,
    version_no INTEGER NOT NULL,
    source_draft_revision BIGINT NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    snapshot_json TEXT NOT NULL,
    json_schema TEXT NOT NULL,
    graphql_sdl TEXT NOT NULL,
    query_contract_json TEXT NOT NULL,
    validation_summary_json TEXT NOT NULL,
    published_by VARCHAR(64) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ontology_version_id_scope UNIQUE (id, workspace_id, org_id),
    CONSTRAINT fk_ontology_version_workspace
        FOREIGN KEY (workspace_id, org_id)
        REFERENCES ontology_workspace(id, org_id) ON DELETE RESTRICT,
    CONSTRAINT uq_ontology_version_workspace_no UNIQUE (workspace_id, version_no)
);

CREATE TABLE ontology_query_audit (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    workspace_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    data_source_id BIGINT,
    user_id VARCHAR(64) NOT NULL,
    concept_key VARCHAR(128) NOT NULL,
    query_json TEXT NOT NULL,
    result_count INTEGER NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    evidence_json TEXT,
    error_code VARCHAR(64),
    sensitive_values_redacted BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ontology_audit_workspace
        FOREIGN KEY (workspace_id, org_id)
        REFERENCES ontology_workspace(id, org_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ontology_audit_version
        FOREIGN KEY (version_id, workspace_id, org_id)
        REFERENCES ontology_version(id, workspace_id, org_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ontology_audit_source
        FOREIGN KEY (data_source_id, workspace_id, org_id)
        REFERENCES ontology_data_source(id, workspace_id, org_id)
        ON DELETE SET NULL (data_source_id)
);

CREATE INDEX idx_ontology_workspace_org_updated
    ON ontology_workspace(org_id, updated_at DESC);
CREATE INDEX idx_ontology_concept_org_workspace
    ON ontology_concept(org_id, workspace_id);
CREATE INDEX idx_ontology_property_org_workspace
    ON ontology_property(org_id, workspace_id, concept_id);
CREATE INDEX idx_ontology_relation_org_workspace
    ON ontology_relation(org_id, workspace_id);
CREATE INDEX idx_ontology_metric_org_workspace
    ON ontology_metric(org_id, workspace_id);
CREATE INDEX idx_ontology_action_org_workspace
    ON ontology_action(org_id, workspace_id);
CREATE INDEX idx_ontology_source_org_workspace
    ON ontology_data_source(org_id, workspace_id);
CREATE INDEX idx_ontology_object_org_workspace
    ON ontology_physical_object(org_id, workspace_id, data_source_id);
CREATE INDEX idx_ontology_field_org_workspace
    ON ontology_physical_field(org_id, workspace_id, physical_object_id);
CREATE INDEX idx_ontology_mapping_org_workspace
    ON ontology_mapping(org_id, workspace_id, target_type, target_key);
CREATE INDEX idx_ontology_proposal_org_workspace
    ON ontology_ai_proposal(org_id, workspace_id, created_at DESC);
CREATE INDEX idx_ontology_version_org_workspace
    ON ontology_version(org_id, workspace_id, version_no DESC);
CREATE INDEX idx_ontology_audit_org_workspace
    ON ontology_query_audit(org_id, workspace_id, created_at DESC);
