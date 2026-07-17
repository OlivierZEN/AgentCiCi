ALTER TABLE ontology_workspace
    ADD COLUMN creation_source VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN reference_package_id VARCHAR(128),
    ADD COLUMN reference_package_fingerprint CHAR(64);

ALTER TABLE ontology_workspace
    ADD CONSTRAINT ck_ontology_workspace_creation_provenance
    CHECK (
        (
            creation_source = 'MANUAL'
            AND reference_package_id IS NULL
            AND reference_package_fingerprint IS NULL
        )
        OR
        (
            creation_source = 'REFERENCE_PACKAGE'
            AND reference_package_id IS NOT NULL
            AND btrim(reference_package_id) <> ''
            AND reference_package_fingerprint IS NOT NULL
            AND reference_package_fingerprint ~ '^[0-9a-f]{64}$'
        )
    );
