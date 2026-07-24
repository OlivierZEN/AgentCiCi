package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ontology_relation")
public class OntologyRelationEntity extends AbstractOntologyWorkspaceEntity {

    @Column(name = "key", nullable = false, length = 128)
    private String key;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_concept_id", nullable = false)
    private Long sourceConceptId;

    @Column(name = "target_concept_id", nullable = false)
    private Long targetConceptId;

    @Column(name = "cardinality", nullable = false, length = 32)
    private String cardinality;

    @Column(name = "forward_label", length = 160)
    private String forwardLabel;

    @Column(name = "reverse_label", length = 160)
    private String reverseLabel;

    @Column(name = "queryable", nullable = false)
    private boolean queryable;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    protected OntologyRelationEntity() {
    }

    public OntologyRelationEntity(
            String companyId,
            Long workspaceId,
            String key,
            String name,
            String description,
            Long sourceConceptId,
            Long targetConceptId,
            String cardinality,
            String forwardLabel,
            String reverseLabel,
            boolean queryable,
            boolean enabled) {
        super(companyId, workspaceId);
        this.key = key;
        this.name = name;
        this.description = description;
        this.sourceConceptId = sourceConceptId;
        this.targetConceptId = targetConceptId;
        this.cardinality = cardinality;
        this.forwardLabel = forwardLabel;
        this.reverseLabel = reverseLabel;
        this.queryable = queryable;
        this.enabled = enabled;
    }

    public String getKey() { return key; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getSourceConceptId() { return sourceConceptId; }
    public Long getTargetConceptId() { return targetConceptId; }
    public String getCardinality() { return cardinality; }
    public String getForwardLabel() { return forwardLabel; }
    public String getReverseLabel() { return reverseLabel; }
    public boolean isQueryable() { return queryable; }
    public boolean isEnabled() { return enabled; }
}
