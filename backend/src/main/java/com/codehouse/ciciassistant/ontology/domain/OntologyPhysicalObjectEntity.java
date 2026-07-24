package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ontology_physical_object")
public class OntologyPhysicalObjectEntity extends AbstractOntologyWorkspaceEntity {

    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;

    @Column(name = "object_key", nullable = false, length = 256)
    private String objectKey;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "object_type", length = 64)
    private String objectType;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    protected OntologyPhysicalObjectEntity() {
    }

    public OntologyPhysicalObjectEntity(
            String companyId,
            Long workspaceId,
            Long dataSourceId,
            String objectKey,
            String name,
            String objectType,
            String metadataJson) {
        super(companyId, workspaceId);
        this.dataSourceId = dataSourceId;
        this.objectKey = objectKey;
        this.name = name;
        this.objectType = objectType;
        this.metadataJson = metadataJson;
        this.discoveredAt = Instant.now();
    }

    public Long getDataSourceId() { return dataSourceId; }
    public String getObjectKey() { return objectKey; }
    public String getName() { return name; }
    public String getObjectType() { return objectType; }
    public String getMetadataJson() { return metadataJson; }
    public Instant getDiscoveredAt() { return discoveredAt; }

    public void refresh(String name, String objectType, String metadataJson) {
        this.name = name;
        this.objectType = objectType;
        this.metadataJson = metadataJson;
        this.discoveredAt = Instant.now();
    }
}
