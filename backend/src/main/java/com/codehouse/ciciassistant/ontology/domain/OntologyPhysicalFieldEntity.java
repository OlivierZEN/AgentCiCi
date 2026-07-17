package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ontology_physical_field")
public class OntologyPhysicalFieldEntity extends AbstractOntologyWorkspaceEntity {

    @Column(name = "physical_object_id", nullable = false)
    private Long physicalObjectId;

    @Column(name = "field_key", nullable = false, length = 256)
    private String fieldKey;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "data_type", nullable = false, length = 64)
    private String dataType;

    @Column(name = "nullable", nullable = false)
    private boolean nullable;

    @Column(name = "multiple", nullable = false)
    private boolean multiple;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    protected OntologyPhysicalFieldEntity() {
    }

    public OntologyPhysicalFieldEntity(
            String orgId,
            Long workspaceId,
            Long physicalObjectId,
            String fieldKey,
            String name,
            String dataType,
            boolean nullable,
            boolean multiple,
            String metadataJson) {
        super(orgId, workspaceId);
        this.physicalObjectId = physicalObjectId;
        this.fieldKey = fieldKey;
        this.name = name;
        this.dataType = dataType;
        this.nullable = nullable;
        this.multiple = multiple;
        this.metadataJson = metadataJson;
        this.discoveredAt = Instant.now();
    }

    public Long getPhysicalObjectId() { return physicalObjectId; }
    public String getFieldKey() { return fieldKey; }
    public String getName() { return name; }
    public String getDataType() { return dataType; }
    public boolean isNullable() { return nullable; }
    public boolean isMultiple() { return multiple; }
    public String getMetadataJson() { return metadataJson; }
    public Instant getDiscoveredAt() { return discoveredAt; }

    public void refresh(
            String name,
            String dataType,
            boolean nullable,
            boolean multiple,
            String metadataJson) {
        this.name = name;
        this.dataType = dataType;
        this.nullable = nullable;
        this.multiple = multiple;
        this.metadataJson = metadataJson;
        this.discoveredAt = Instant.now();
    }
}
