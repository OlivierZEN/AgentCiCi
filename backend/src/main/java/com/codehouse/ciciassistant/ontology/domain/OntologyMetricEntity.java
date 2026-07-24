package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ontology_metric")
public class OntologyMetricEntity extends AbstractOntologyWorkspaceEntity {

    @Column(name = "key", nullable = false, length = 128)
    private String key;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "concept_id", nullable = false)
    private Long conceptId;

    @Column(name = "aggregation", nullable = false, length = 32)
    private String aggregation;

    @Column(name = "measure_property_key", length = 128)
    private String measurePropertyKey;

    @Column(name = "group_by_property_keys_json", columnDefinition = "TEXT")
    private String groupByPropertyKeysJson;

    @Column(name = "time_property_key", length = 128)
    private String timePropertyKey;

    @Column(name = "filters_json", columnDefinition = "TEXT")
    private String filtersJson;

    protected OntologyMetricEntity() {
    }

    public OntologyMetricEntity(
            String companyId,
            Long workspaceId,
            String key,
            String name,
            Long conceptId,
            String aggregation,
            String measurePropertyKey,
            String groupByPropertyKeysJson,
            String timePropertyKey,
            String filtersJson) {
        super(companyId, workspaceId);
        this.key = key;
        this.name = name;
        this.conceptId = conceptId;
        this.aggregation = aggregation;
        this.measurePropertyKey = measurePropertyKey;
        this.groupByPropertyKeysJson = groupByPropertyKeysJson;
        this.timePropertyKey = timePropertyKey;
        this.filtersJson = filtersJson;
    }

    public String getKey() { return key; }
    public String getName() { return name; }
    public Long getConceptId() { return conceptId; }
    public String getAggregation() { return aggregation; }
    public String getMeasurePropertyKey() { return measurePropertyKey; }
    public String getGroupByPropertyKeysJson() { return groupByPropertyKeysJson; }
    public String getTimePropertyKey() { return timePropertyKey; }
    public String getFiltersJson() { return filtersJson; }
}
