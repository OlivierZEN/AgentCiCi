package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ontology_action")
public class OntologyActionEntity extends AbstractOntologyWorkspaceEntity {

    @Column(name = "key", nullable = false, length = 128)
    private String key;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "concept_id", nullable = false)
    private Long conceptId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "parameters_json", nullable = false, columnDefinition = "TEXT")
    private String parametersJson;

    protected OntologyActionEntity() {
    }

    public OntologyActionEntity(
            String companyId,
            Long workspaceId,
            String key,
            String name,
            Long conceptId,
            String description,
            String parametersJson) {
        super(companyId, workspaceId);
        this.key = key;
        this.name = name;
        this.conceptId = conceptId;
        this.description = description;
        this.parametersJson = parametersJson;
    }

    public String getKey() { return key; }
    public String getName() { return name; }
    public Long getConceptId() { return conceptId; }
    public String getDescription() { return description; }
    public String getParametersJson() { return parametersJson; }
}
