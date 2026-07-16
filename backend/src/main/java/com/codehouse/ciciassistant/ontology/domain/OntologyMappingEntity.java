package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ontology_mapping")
public class OntologyMappingEntity extends AbstractOntologyWorkspaceEntity {

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_key", nullable = false, length = 256)
    private String targetKey;

    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;

    @Column(name = "physical_object_key", nullable = false, length = 256)
    private String physicalObjectKey;

    @Column(name = "physical_field_key", length = 256)
    private String physicalFieldKey;

    @Column(name = "relation_target_field_key", length = 256)
    private String relationTargetFieldKey;

    @Column(name = "transform", columnDefinition = "TEXT")
    private String transform;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "validation_status", nullable = false, length = 32)
    private String validationStatus;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    protected OntologyMappingEntity() {
    }

    public OntologyMappingEntity(
            String orgId,
            Long workspaceId,
            String targetType,
            String targetKey,
            Long dataSourceId,
            String physicalObjectKey,
            String physicalFieldKey,
            String relationTargetFieldKey,
            String transform,
            BigDecimal confidence,
            String source,
            String validationStatus,
            String createdBy) {
        super(orgId, workspaceId);
        this.targetType = targetType;
        this.targetKey = targetKey;
        this.dataSourceId = dataSourceId;
        this.physicalObjectKey = physicalObjectKey;
        this.physicalFieldKey = physicalFieldKey;
        this.relationTargetFieldKey = relationTargetFieldKey;
        this.transform = transform;
        this.confidence = confidence;
        this.source = source;
        this.validationStatus = validationStatus;
        this.createdBy = createdBy;
    }

    public String getTargetType() { return targetType; }
    public String getTargetKey() { return targetKey; }
    public Long getDataSourceId() { return dataSourceId; }
    public String getPhysicalObjectKey() { return physicalObjectKey; }
    public String getPhysicalFieldKey() { return physicalFieldKey; }
    public String getRelationTargetFieldKey() { return relationTargetFieldKey; }
    public String getTransform() { return transform; }
    public BigDecimal getConfidence() { return confidence; }
    public String getSource() { return source; }
    public String getValidationStatus() { return validationStatus; }
    public Instant getLastValidatedAt() { return lastValidatedAt; }
    public String getCreatedBy() { return createdBy; }

    public void applyValidation(boolean valid) {
        this.validationStatus = valid ? "VALID" : "INVALID";
        this.lastValidatedAt = Instant.now();
    }
}
