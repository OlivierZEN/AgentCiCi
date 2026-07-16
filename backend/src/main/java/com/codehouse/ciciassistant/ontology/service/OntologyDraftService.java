package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.ontology.domain.OntologyActionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyActionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyConceptEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyConceptRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyMetricEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyMetricRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPropertyEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyPropertyRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyRelationEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyRelationRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OntologyDraftService {

    private final OntologyWorkspaceRepository workspaces;
    private final OntologyConceptRepository concepts;
    private final OntologyPropertyRepository properties;
    private final OntologyRelationRepository relations;
    private final OntologyMetricRepository metrics;
    private final OntologyActionRepository actions;
    private final OntologyDataSourceRepository dataSources;
    private final OntologyMappingRepository mappings;
    private final OntologyTenantPersistence persistence;
    private final ObjectMapper objectMapper;

    public OntologyDraftService(
            OntologyWorkspaceRepository workspaces,
            OntologyConceptRepository concepts,
            OntologyPropertyRepository properties,
            OntologyRelationRepository relations,
            OntologyMetricRepository metrics,
            OntologyActionRepository actions,
            OntologyDataSourceRepository dataSources,
            OntologyMappingRepository mappings,
            OntologyTenantPersistence persistence,
            ObjectMapper objectMapper) {
        this.workspaces = workspaces;
        this.concepts = concepts;
        this.properties = properties;
        this.relations = relations;
        this.metrics = metrics;
        this.actions = actions;
        this.dataSources = dataSources;
        this.mappings = mappings;
        this.persistence = persistence;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OntologyWorkspaceEntity saveDraft(
            String orgId,
            String userId,
            Long workspaceId,
            Long expectedRevision,
            OntologyDocument document) {
        requireCurrentOrg(orgId);
        Objects.requireNonNull(document, "document");
        OntologyWorkspaceEntity workspace = requireWorkspace(orgId, workspaceId);
        if (!Objects.equals(workspace.getDraftRevision(), expectedRevision)) {
            throw new ConflictException("ONTOLOGY_REVISION_CONFLICT");
        }

        deleteDraftChildren(workspaceId, orgId);
        Map<String, Long> conceptIds = saveConcepts(orgId, workspaceId, document);
        Map<Long, Long> dataSourceIds = saveDataSources(orgId, userId, workspaceId, document);
        saveRelations(orgId, workspaceId, document, conceptIds);
        saveMetrics(orgId, workspaceId, document, conceptIds);
        saveActions(orgId, workspaceId, document, conceptIds);
        saveMappings(orgId, userId, workspaceId, document, dataSourceIds);

        workspace.applyDraftMetadata(
                document.key(), document.name(), document.description(), userId);
        return persistence.saveForCurrentOrg(workspace);
    }

    OntologyDocument loadDraft(
            String orgId,
            Long workspaceId,
            OntologyWorkspaceEntity workspace) {
        requireCurrentOrg(orgId);
        if (workspace == null
                || !Objects.equals(workspace.getId(), workspaceId)
                || !Objects.equals(workspace.getOrgId(), orgId)) {
            throw new ResourceNotFoundException("Ontology workspace not found");
        }

        List<OntologyConceptEntity> conceptEntities =
                concepts.findByWorkspaceIdAndOrgIdOrderByIdAsc(workspaceId, orgId);
        Map<Long, String> conceptKeys = new HashMap<>();
        List<OntologyDocument.Concept> conceptDocuments = conceptEntities.stream()
                .map(entity -> {
                    conceptKeys.put(entity.getId(), entity.getKey());
                    List<OntologyDocument.Property> propertyDocuments =
                            properties.findByConceptIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                                            entity.getId(), workspaceId, orgId).stream()
                                    .map(this::toDocumentProperty)
                                    .toList();
                    return new OntologyDocument.Concept(
                            entity.getKey(),
                            entity.getName(),
                            entity.getPluralName(),
                            entity.getDescription(),
                            OntologyDocument.ConceptType.valueOf(entity.getConceptType()),
                            entity.getDisplayPropertyKey(),
                            entity.getPositionX(),
                            entity.getPositionY(),
                            entity.isQueryable(),
                            entity.isEnabled(),
                            propertyDocuments);
                })
                .toList();

        List<OntologyDocument.Relation> relationDocuments =
                relations.findByWorkspaceIdAndOrgIdOrderByIdAsc(workspaceId, orgId).stream()
                        .map(entity -> new OntologyDocument.Relation(
                                entity.getKey(),
                                entity.getName(),
                                entity.getDescription(),
                                requiredConceptKey(conceptKeys, entity.getSourceConceptId()),
                                requiredConceptKey(conceptKeys, entity.getTargetConceptId()),
                                OntologyDocument.Cardinality.valueOf(entity.getCardinality()),
                                entity.getForwardLabel(),
                                entity.getReverseLabel(),
                                entity.isQueryable(),
                                entity.isEnabled()))
                        .toList();
        List<OntologyDocument.Metric> metricDocuments =
                metrics.findByWorkspaceIdAndOrgIdOrderByIdAsc(workspaceId, orgId).stream()
                        .map(entity -> new OntologyDocument.Metric(
                                entity.getKey(),
                                entity.getName(),
                                requiredConceptKey(conceptKeys, entity.getConceptId()),
                                OntologyDocument.Aggregation.valueOf(entity.getAggregation()),
                                entity.getMeasurePropertyKey(),
                                readList(entity.getGroupByPropertyKeysJson(), new TypeReference<>() { }),
                                entity.getTimePropertyKey(),
                                readList(entity.getFiltersJson(), new TypeReference<>() { })))
                        .toList();
        List<OntologyDocument.Action> actionDocuments =
                actions.findByWorkspaceIdAndOrgIdOrderByIdAsc(workspaceId, orgId).stream()
                        .map(entity -> new OntologyDocument.Action(
                                entity.getKey(),
                                entity.getName(),
                                requiredConceptKey(conceptKeys, entity.getConceptId()),
                                entity.getDescription(),
                                readList(entity.getParametersJson(), new TypeReference<>() { })))
                        .toList();
        List<OntologyDocument.DataSource> dataSourceDocuments =
                dataSources.findByWorkspaceIdAndOrgIdOrderByIdAsc(workspaceId, orgId).stream()
                        .map(entity -> new OntologyDocument.DataSource(
                                entity.getId(),
                                entity.getKey(),
                                entity.getName(),
                                OntologyDocument.SourceType.valueOf(entity.getSourceType()),
                                entity.getConfigJson()))
                        .toList();
        List<OntologyDocument.Mapping> mappingDocuments =
                mappings.findByWorkspaceIdAndOrgIdOrderByIdAsc(workspaceId, orgId).stream()
                        .map(entity -> new OntologyDocument.Mapping(
                                entity.getTargetType(),
                                entity.getTargetKey(),
                                entity.getDataSourceId(),
                                entity.getPhysicalObjectKey(),
                                entity.getPhysicalFieldKey(),
                                entity.getRelationTargetFieldKey(),
                                entity.getTransform(),
                                entity.getConfidence().doubleValue(),
                                entity.getSource(),
                                entity.getValidationStatus()))
                        .toList();
        return new OntologyDocument(
                workspace.getKey(),
                workspace.getName(),
                workspace.getDescription(),
                conceptDocuments,
                relationDocuments,
                metricDocuments,
                actionDocuments,
                dataSourceDocuments,
                mappingDocuments);
    }

    private void deleteDraftChildren(Long workspaceId, String orgId) {
        mappings.deleteByWorkspaceIdAndOrgId(workspaceId, orgId);
        properties.deleteByWorkspaceIdAndOrgId(workspaceId, orgId);
        relations.deleteByWorkspaceIdAndOrgId(workspaceId, orgId);
        metrics.deleteByWorkspaceIdAndOrgId(workspaceId, orgId);
        actions.deleteByWorkspaceIdAndOrgId(workspaceId, orgId);
        concepts.deleteByWorkspaceIdAndOrgId(workspaceId, orgId);
        persistence.flushForCurrentOrg(orgId);
    }

    private Map<String, Long> saveConcepts(
            String orgId,
            Long workspaceId,
            OntologyDocument document) {
        Map<String, Long> conceptIds = new HashMap<>();
        for (OntologyDocument.Concept concept : safe(document.concepts())) {
            OntologyConceptEntity saved = persistence.saveForCurrentOrg(new OntologyConceptEntity(
                    orgId,
                    workspaceId,
                    concept.key(),
                    concept.name(),
                    concept.pluralName(),
                    concept.description(),
                    concept.conceptType().name(),
                    concept.displayPropertyKey(),
                    concept.positionX(),
                    concept.positionY(),
                    concept.queryable(),
                    concept.enabled()));
            conceptIds.put(concept.key(), saved.getId());
            for (OntologyDocument.Property property : safe(concept.properties())) {
                persistence.saveForCurrentOrg(new OntologyPropertyEntity(
                        orgId,
                        workspaceId,
                        saved.getId(),
                        property.key(),
                        property.name(),
                        property.description(),
                        property.dataType().name(),
                        property.required(),
                        property.multiple(),
                        property.sensitive(),
                        property.queryable(),
                        writeJson(safe(property.enumValues())),
                        null,
                        null));
            }
        }
        return conceptIds;
    }

    private Map<Long, Long> saveDataSources(
            String orgId,
            String userId,
            Long workspaceId,
            OntologyDocument document) {
        Map<Long, Long> sourceIds = new HashMap<>();
        for (OntologyDocument.DataSource source : safe(document.dataSources())) {
            OntologyDataSourceEntity entity = source.id() == null
                    ? null
                    : dataSources.findByIdAndWorkspaceIdAndOrgId(
                            source.id(), workspaceId, orgId).orElse(null);
            if (entity == null) {
                entity = dataSources.findByWorkspaceIdAndOrgIdAndKey(
                        workspaceId, orgId, source.key()).orElse(null);
            }
            if (entity == null) {
                entity = new OntologyDataSourceEntity(
                        orgId,
                        workspaceId,
                        source.key(),
                        source.name(),
                        source.type().name(),
                        source.configJson(),
                        null,
                        userId);
            } else {
                entity.updateDraft(source.name(), source.type().name(), source.configJson());
            }
            OntologyDataSourceEntity saved = persistence.saveForCurrentOrg(entity);
            if (source.id() != null) {
                sourceIds.put(source.id(), saved.getId());
            }
            sourceIds.put(saved.getId(), saved.getId());
        }
        return sourceIds;
    }

    private void saveRelations(
            String orgId,
            Long workspaceId,
            OntologyDocument document,
            Map<String, Long> conceptIds) {
        for (OntologyDocument.Relation relation : safe(document.relations())) {
            persistence.saveForCurrentOrg(new OntologyRelationEntity(
                    orgId,
                    workspaceId,
                    relation.key(),
                    relation.name(),
                    relation.description(),
                    requiredId(conceptIds, relation.sourceConceptKey(), "relation source"),
                    requiredId(conceptIds, relation.targetConceptKey(), "relation target"),
                    relation.cardinality().name(),
                    relation.forwardLabel(),
                    relation.reverseLabel(),
                    relation.queryable(),
                    relation.enabled()));
        }
    }

    private void saveMetrics(
            String orgId,
            Long workspaceId,
            OntologyDocument document,
            Map<String, Long> conceptIds) {
        for (OntologyDocument.Metric metric : safe(document.metrics())) {
            persistence.saveForCurrentOrg(new OntologyMetricEntity(
                    orgId,
                    workspaceId,
                    metric.key(),
                    metric.name(),
                    requiredId(conceptIds, metric.conceptKey(), "metric concept"),
                    metric.aggregation().name(),
                    metric.measurePropertyKey(),
                    writeJson(safe(metric.groupByPropertyKeys())),
                    metric.timePropertyKey(),
                    writeJson(safe(metric.filters()))));
        }
    }

    private void saveActions(
            String orgId,
            Long workspaceId,
            OntologyDocument document,
            Map<String, Long> conceptIds) {
        for (OntologyDocument.Action action : safe(document.actions())) {
            persistence.saveForCurrentOrg(new OntologyActionEntity(
                    orgId,
                    workspaceId,
                    action.key(),
                    action.name(),
                    requiredId(conceptIds, action.conceptKey(), "action concept"),
                    action.description(),
                    writeJson(safe(action.parameters()))));
        }
    }

    private void saveMappings(
            String orgId,
            String userId,
            Long workspaceId,
            OntologyDocument document,
            Map<Long, Long> dataSourceIds) {
        for (OntologyDocument.Mapping mapping : safe(document.mappings())) {
            Long dataSourceId = requiredId(
                    dataSourceIds, mapping.dataSourceId(), "mapping data source");
            persistence.saveForCurrentOrg(new OntologyMappingEntity(
                    orgId,
                    workspaceId,
                    mapping.targetType(),
                    mapping.targetKey(),
                    dataSourceId,
                    mapping.physicalObjectKey(),
                    mapping.physicalFieldKey(),
                    mapping.relationTargetFieldKey(),
                    mapping.transform(),
                    BigDecimal.valueOf(mapping.confidence()),
                    mapping.source(),
                    mapping.validationStatus(),
                    userId));
        }
    }

    private OntologyDocument.Property toDocumentProperty(OntologyPropertyEntity entity) {
        return new OntologyDocument.Property(
                entity.getKey(),
                entity.getName(),
                entity.getDescription(),
                OntologyDocument.DataType.valueOf(entity.getDataType()),
                entity.isRequired(),
                entity.isMultiple(),
                entity.isSensitive(),
                entity.isQueryable(),
                readList(entity.getEnumValuesJson(), new TypeReference<>() { }));
    }

    private OntologyWorkspaceEntity requireWorkspace(String orgId, Long workspaceId) {
        return workspaces.findForUpdateByIdAndOrgId(workspaceId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Ontology workspace not found"));
    }

    private void requireCurrentOrg(String orgId) {
        if (!Objects.equals(TenantContext.requireOrgId(), orgId)) {
            throw new ForbiddenException("Ontology organization does not match current organization");
        }
    }

    private <K> Long requiredId(Map<K, Long> ids, K key, String label) {
        Long id = ids.get(key);
        if (id == null) {
            throw new IllegalArgumentException("Missing " + label + ": " + key);
        }
        return id;
    }

    private String requiredConceptKey(Map<Long, String> conceptKeys, Long conceptId) {
        String key = conceptKeys.get(conceptId);
        if (key == null) {
            throw new IllegalStateException("Draft references an unknown concept id: " + conceptId);
        }
        return key;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize ontology draft", exception);
        }
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read persisted ontology draft", exception);
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
