package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ontology_property")
public class OntologyPropertyEntity extends AbstractOntologyWorkspaceEntity {

    @Column(name = "concept_id", nullable = false)
    private Long conceptId;

    @Column(name = "key", nullable = false, length = 128)
    private String key;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "data_type", nullable = false, length = 32)
    private String dataType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "multiple", nullable = false)
    private boolean multiple;

    @Column(name = "sensitive", nullable = false)
    private boolean sensitive;

    @Column(name = "queryable", nullable = false)
    private boolean queryable;

    @Column(name = "enum_values_json", columnDefinition = "TEXT")
    private String enumValuesJson;

    @Column(name = "format_hint", length = 128)
    private String formatHint;

    @Column(name = "display_strategy", length = 64)
    private String displayStrategy;

    protected OntologyPropertyEntity() {
    }

    public OntologyPropertyEntity(
            String orgId,
            Long workspaceId,
            Long conceptId,
            String key,
            String name,
            String description,
            String dataType,
            boolean required,
            boolean multiple,
            boolean sensitive,
            boolean queryable,
            String enumValuesJson,
            String formatHint,
            String displayStrategy) {
        super(orgId, workspaceId);
        this.conceptId = conceptId;
        this.key = key;
        this.name = name;
        this.description = description;
        this.dataType = dataType;
        this.required = required;
        this.multiple = multiple;
        this.sensitive = sensitive;
        this.queryable = queryable;
        this.enumValuesJson = enumValuesJson;
        this.formatHint = formatHint;
        this.displayStrategy = displayStrategy;
    }

    public Long getConceptId() { return conceptId; }
    public String getKey() { return key; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getDataType() { return dataType; }
    public boolean isRequired() { return required; }
    public boolean isMultiple() { return multiple; }
    public boolean isSensitive() { return sensitive; }
    public boolean isQueryable() { return queryable; }
    public String getEnumValuesJson() { return enumValuesJson; }
    public String getFormatHint() { return formatHint; }
    public String getDisplayStrategy() { return displayStrategy; }
}
