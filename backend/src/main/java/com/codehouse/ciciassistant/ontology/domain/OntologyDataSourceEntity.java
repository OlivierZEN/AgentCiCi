package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ontology_data_source")
public class OntologyDataSourceEntity extends AbstractOntologyWorkspaceEntity {

    @Column(name = "key", nullable = false, length = 128)
    private String key;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "sample_data_json", columnDefinition = "TEXT")
    private String sampleDataJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    protected OntologyDataSourceEntity() {
    }

    public OntologyDataSourceEntity(
            String companyId,
            Long workspaceId,
            String key,
            String name,
            String sourceType,
            String configJson,
            String sampleDataJson,
            String createdBy) {
        super(companyId, workspaceId);
        this.key = key;
        this.name = name;
        this.sourceType = sourceType;
        this.configJson = configJson;
        this.sampleDataJson = sampleDataJson;
        this.status = "DRAFT";
        this.createdBy = createdBy;
    }

    public String getKey() { return key; }
    public String getName() { return name; }
    public String getSourceType() { return sourceType; }
    public String getConfigJson() { return configJson; }
    public String getSampleDataJson() { return sampleDataJson; }
    public String getStatus() { return status; }
    public Instant getLastValidatedAt() { return lastValidatedAt; }
    public String getCreatedBy() { return createdBy; }

    public boolean definitionMatches(
            String key,
            String sourceType,
            String configJson,
            String sampleDataJson) {
        return java.util.Objects.equals(this.key, key)
                && java.util.Objects.equals(this.sourceType, sourceType)
                && java.util.Objects.equals(this.configJson, configJson)
                && java.util.Objects.equals(this.sampleDataJson, sampleDataJson);
    }

    public void updateDraft(
            String name,
            String sourceType,
            String configJson,
            String sampleDataJson) {
        this.name = name;
        this.sourceType = sourceType;
        this.configJson = configJson;
        this.sampleDataJson = sampleDataJson;
    }
}
