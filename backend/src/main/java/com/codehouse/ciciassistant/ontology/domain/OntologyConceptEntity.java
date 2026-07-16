package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ontology_concept")
public class OntologyConceptEntity extends AbstractOntologyWorkspaceEntity {

    @Column(name = "key", nullable = false, length = 128)
    private String key;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "plural_name", length = 160)
    private String pluralName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "concept_type", nullable = false, length = 32)
    private String conceptType;

    @Column(name = "display_property_key", length = 128)
    private String displayPropertyKey;

    @Column(name = "position_x", nullable = false)
    private double positionX;

    @Column(name = "position_y", nullable = false)
    private double positionY;

    @Column(name = "queryable", nullable = false)
    private boolean queryable;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    protected OntologyConceptEntity() {
    }

    public OntologyConceptEntity(
            String orgId,
            Long workspaceId,
            String key,
            String name,
            String pluralName,
            String description,
            String conceptType,
            String displayPropertyKey,
            double positionX,
            double positionY,
            boolean queryable,
            boolean enabled) {
        super(orgId, workspaceId);
        this.key = key;
        this.name = name;
        this.pluralName = pluralName;
        this.description = description;
        this.conceptType = conceptType;
        this.displayPropertyKey = displayPropertyKey;
        this.positionX = positionX;
        this.positionY = positionY;
        this.queryable = queryable;
        this.enabled = enabled;
    }

    public String getKey() { return key; }
    public String getName() { return name; }
    public String getPluralName() { return pluralName; }
    public String getDescription() { return description; }
    public String getConceptType() { return conceptType; }
    public String getDisplayPropertyKey() { return displayPropertyKey; }
    public double getPositionX() { return positionX; }
    public double getPositionY() { return positionY; }
    public boolean isQueryable() { return queryable; }
    public boolean isEnabled() { return enabled; }
}
