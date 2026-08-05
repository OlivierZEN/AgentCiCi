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
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectRepository;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final OntologyPhysicalObjectRepository physicalObjects;
    private final OntologyMappingRepository mappings;
    private final OntologyTenantPersistence persistence;
    private final OntologyDataSourcePolicy dataSourcePolicy;
    private final OntologyDraftSafetyPolicy draftSafety;
    private final ObjectMapper objectMapper;

    public OntologyDraftService(
            OntologyWorkspaceRepository workspaces,
            OntologyConceptRepository concepts,
            OntologyPropertyRepository properties,
            OntologyRelationRepository relations,
            OntologyMetricRepository metrics,
            OntologyActionRepository actions,
            OntologyDataSourceRepository dataSources,
            OntologyPhysicalObjectRepository physicalObjects,
            OntologyMappingRepository mappings,
            OntologyTenantPersistence persistence,
            OntologyDataSourcePolicy dataSourcePolicy,
            OntologyDraftSafetyPolicy draftSafety,
            ObjectMapper objectMapper) {
        this.workspaces = workspaces;
        this.concepts = concepts;
        this.properties = properties;
        this.relations = relations;
        this.metrics = metrics;
        this.actions = actions;
        this.dataSources = dataSources;
        this.physicalObjects = physicalObjects;
        this.mappings = mappings;
        this.persistence = persistence;
        this.dataSourcePolicy = dataSourcePolicy;
        this.draftSafety = draftSafety;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OntologyWorkspaceEntity saveDraft(
            String companyId,
            String userId,
            Long workspaceId,
            Long expectedRevision,
            OntologyDocument document) {
        requireCurrentOrg(companyId);
        draftSafety.validateDocument(document);
        OntologyWorkspaceEntity workspace = requireWorkspace(companyId, workspaceId);
        if (!Objects.equals(workspace.getDraftRevision(), expectedRevision)) {
            throw new ConflictException("ONTOLOGY_REVISION_CONFLICT");
        }
        if ("ARCHIVED".equals(workspace.getStatus())) {
            throw new ConflictException("ONTOLOGY_WORKSPACE_ARCHIVED");
        }
        if (!Objects.equals(workspace.getKey(), document.key())) {
            throw new ConflictException("ONTOLOGY_KEY_IMMUTABLE");
        }

        List<OntologyMappingEntity> existingMappings =
                mappings.findByWorkspaceIdAndCompanyIdOrderByIdAsc(workspaceId, companyId);
        Set<String> changedRelationKeys = changedRelationKeys(companyId, workspaceId, document);
        deleteDraftChildren(workspaceId, companyId);
        Map<String, Long> conceptIds = saveConcepts(companyId, workspaceId, document);
        SavedDataSources savedSources = saveDataSources(
                companyId, userId, workspaceId, document);
        clearChangedCatalog(companyId, workspaceId, savedSources.changedIds());
        saveRelations(companyId, workspaceId, document, conceptIds);
        saveMetrics(companyId, workspaceId, document, conceptIds);
        saveActions(companyId, workspaceId, document, conceptIds);
        saveMappings(
                companyId,
                userId,
                workspaceId,
                document,
                savedSources.ids(),
                existingMappings,
                changedRelationKeys,
                savedSources.changedIds());

        workspace.applyDraftMetadata(
                document.name(), document.description(), userId);
        return persistence.saveForCurrentOrg(workspace);
    }

    public OntologyDocument loadDraft(
            String companyId,
            Long workspaceId,
            OntologyWorkspaceEntity workspace) {
        requireCurrentOrg(companyId);
        if (workspace == null
                || !Objects.equals(workspace.getId(), workspaceId)
                || !Objects.equals(workspace.getCompanyId(), companyId)) {
            throw new ResourceNotFoundException("Ontology workspace not found");
        }

        List<OntologyConceptEntity> conceptEntities =
                concepts.findByWorkspaceIdAndCompanyIdOrderByIdAsc(workspaceId, companyId);
        Map<Long, String> conceptKeys = new HashMap<>();
        List<OntologyDocument.Concept> conceptDocuments = conceptEntities.stream()
                .map(entity -> {
                    conceptKeys.put(entity.getId(), entity.getKey());
                    List<OntologyDocument.Property> propertyDocuments =
                            properties.findByConceptIdAndWorkspaceIdAndCompanyIdOrderByIdAsc(
                                            entity.getId(), workspaceId, companyId).stream()
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
                relations.findByWorkspaceIdAndCompanyIdOrderByIdAsc(workspaceId, companyId).stream()
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
                metrics.findByWorkspaceIdAndCompanyIdOrderByIdAsc(workspaceId, companyId).stream()
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
                actions.findByWorkspaceIdAndCompanyIdOrderByIdAsc(workspaceId, companyId).stream()
                        .map(entity -> new OntologyDocument.Action(
                                entity.getKey(),
                                entity.getName(),
                                requiredConceptKey(conceptKeys, entity.getConceptId()),
                                entity.getDescription(),
                                readList(entity.getParametersJson(), new TypeReference<>() { })))
                        .toList();
        List<OntologyDocument.DataSource> dataSourceDocuments =
                dataSources.findByWorkspaceIdAndCompanyIdOrderByIdAsc(workspaceId, companyId).stream()
                        .map(entity -> new OntologyDocument.DataSource(
                                entity.getId(),
                                entity.getKey(),
                                entity.getName(),
                                OntologyDocument.SourceType.valueOf(entity.getSourceType()),
                                entity.getConfigJson(),
                                entity.getSampleDataJson()))
                        .toList();
        List<OntologyDocument.Mapping> mappingDocuments =
                mappings.findByWorkspaceIdAndCompanyIdOrderByIdAsc(workspaceId, companyId).stream()
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

    private void deleteDraftChildren(Long workspaceId, String companyId) {
        properties.deleteByWorkspaceIdAndCompanyId(workspaceId, companyId);
        relations.deleteByWorkspaceIdAndCompanyId(workspaceId, companyId);
        metrics.deleteByWorkspaceIdAndCompanyId(workspaceId, companyId);
        actions.deleteByWorkspaceIdAndCompanyId(workspaceId, companyId);
        concepts.deleteByWorkspaceIdAndCompanyId(workspaceId, companyId);
        persistence.flushForCurrentOrg(companyId);
    }

    private Map<String, Long> saveConcepts(
            String companyId,
            Long workspaceId,
            OntologyDocument document) {
        Map<String, Long> conceptIds = new HashMap<>();
        for (OntologyDocument.Concept concept : safe(document.concepts())) {
            OntologyConceptEntity saved = persistence.saveForCurrentOrg(new OntologyConceptEntity(
                    companyId,
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
                        companyId,
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

    private SavedDataSources saveDataSources(
            String companyId,
            String userId,
            Long workspaceId,
            OntologyDocument document) {
        Map<Long, Long> sourceIds = new HashMap<>();
        Set<Long> changedIds = new LinkedHashSet<>();
        for (OntologyDocument.DataSource source : safe(document.dataSources())) {
            dataSourcePolicy.validate(source);
            OntologyDataSourceEntity entity = source.id() == null
                    ? null
                    : dataSources.findByIdAndWorkspaceIdAndCompanyId(
                            source.id(), workspaceId, companyId).orElse(null);
            if (entity == null) {
                entity = dataSources.findByWorkspaceIdAndCompanyIdAndKey(
                        workspaceId, companyId, source.key()).orElse(null);
            }
            if (entity == null) {
                entity = new OntologyDataSourceEntity(
                        companyId,
                        workspaceId,
                        source.key(),
                        source.name(),
                        source.type().name(),
                        source.configJson(),
                        source.sampleDataJson(),
                        userId);
            } else if (!Objects.equals(entity.getKey(), source.key())) {
                throw new ConflictException("ONTOLOGY_DATA_SOURCE_KEY_IMMUTABLE");
            } else {
                if (!entity.definitionMatches(
                        source.key(),
                        source.type().name(),
                        source.configJson(),
                        source.sampleDataJson())) {
                    changedIds.add(entity.getId());
                }
                entity.updateDraft(
                        source.name(),
                        source.type().name(),
                        source.configJson(),
                        source.sampleDataJson());
            }
            OntologyDataSourceEntity saved = persistence.saveForCurrentOrg(entity);
            if (source.id() != null) {
                sourceIds.put(source.id(), saved.getId());
            }
            sourceIds.put(saved.getId(), saved.getId());
        }
        return new SavedDataSources(Map.copyOf(sourceIds), Set.copyOf(changedIds));
    }

    private void saveRelations(
            String companyId,
            Long workspaceId,
            OntologyDocument document,
            Map<String, Long> conceptIds) {
        for (OntologyDocument.Relation relation : safe(document.relations())) {
            persistence.saveForCurrentOrg(new OntologyRelationEntity(
                    companyId,
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
            String companyId,
            Long workspaceId,
            OntologyDocument document,
            Map<String, Long> conceptIds) {
        for (OntologyDocument.Metric metric : safe(document.metrics())) {
            persistence.saveForCurrentOrg(new OntologyMetricEntity(
                    companyId,
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
            String companyId,
            Long workspaceId,
            OntologyDocument document,
            Map<String, Long> conceptIds) {
        for (OntologyDocument.Action action : safe(document.actions())) {
            persistence.saveForCurrentOrg(new OntologyActionEntity(
                    companyId,
                    workspaceId,
                    action.key(),
                    action.name(),
                    requiredId(conceptIds, action.conceptKey(), "action concept"),
                    action.description(),
                    writeJson(safe(action.parameters()))));
        }
    }

    private void saveMappings(
            String companyId,
            String userId,
            Long workspaceId,
            OntologyDocument document,
            Map<Long, Long> dataSourceIds,
            List<OntologyMappingEntity> existingMappings,
            Set<String> changedRelationKeys,
            Set<Long> changedDataSourceIds) {
        Map<String, OntologyMappingEntity> existingByIdentity = new LinkedHashMap<>();
        for (OntologyMappingEntity existing : safe(existingMappings)) {
            String identity = mappingIdentity(
                    existing.getTargetType(),
                    existing.getTargetKey(),
                    existing.getDataSourceId());
            if (existingByIdentity.putIfAbsent(identity, existing) != null) {
                throw new IllegalStateException("MAPPING_AMBIGUOUS");
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        for (OntologyDocument.Mapping mapping : safe(document.mappings())) {
            Long dataSourceId = requiredId(
                    dataSourceIds, mapping.dataSourceId(), "mapping data source");
            String targetType = normalizedTargetType(mapping.targetType());
            String identity = mappingIdentity(targetType, mapping.targetKey(), dataSourceId);
            if (!seen.add(identity)) {
                throw new IllegalArgumentException("MAPPING_DUPLICATE_IDENTITY");
            }
            BigDecimal confidence = BigDecimal.valueOf(mapping.confidence());
            OntologyMappingEntity existing = existingByIdentity.get(identity);
            if (existing == null) {
                persistence.saveForCurrentOrg(new OntologyMappingEntity(
                        companyId,
                        workspaceId,
                        targetType,
                        mapping.targetKey(),
                        dataSourceId,
                        mapping.physicalObjectKey(),
                        mapping.physicalFieldKey(),
                        mapping.relationTargetFieldKey(),
                        mapping.transform(),
                        confidence,
                        serverOrigin(mapping.source()),
                        "PENDING",
                        userId));
                continue;
            }
            boolean relationEndpointsChanged = "RELATION".equals(targetType)
                    && changedRelationKeys.contains(mapping.targetKey());
            boolean sourceDefinitionChanged = changedDataSourceIds.contains(dataSourceId);
            boolean mappingDefinitionChanged = !existing.definitionMatches(
                    mapping.physicalObjectKey(),
                    mapping.physicalFieldKey(),
                    mapping.relationTargetFieldKey(),
                    mapping.transform(),
                    confidence);
            if (mappingDefinitionChanged) {
                existing.updateDefinition(
                        mapping.physicalObjectKey(),
                        mapping.physicalFieldKey(),
                        mapping.relationTargetFieldKey(),
                        mapping.transform(),
                        confidence,
                        serverOrigin(mapping.source()));
            } else if (relationEndpointsChanged || sourceDefinitionChanged) {
                existing.markPending();
            }
            if (mappingDefinitionChanged || relationEndpointsChanged || sourceDefinitionChanged) {
                persistence.saveForCurrentOrg(existing);
            }
        }
        existingByIdentity.forEach((identity, entity) -> {
            if (!seen.contains(identity)) {
                persistence.deleteForCurrentOrg(
                        companyId,
                        () -> mappings.deleteByIdAndWorkspaceIdAndCompanyId(
                                entity.getId(), workspaceId, companyId));
            }
        });
    }

    private void clearChangedCatalog(
            String companyId,
            Long workspaceId,
            Set<Long> changedDataSourceIds) {
        for (Long dataSourceId : changedDataSourceIds) {
            persistence.deleteForCurrentOrg(
                    companyId,
                    () -> physicalObjects.deleteByDataSourceIdAndWorkspaceIdAndCompanyId(
                            dataSourceId, workspaceId, companyId));
        }
    }

    private Set<String> changedRelationKeys(
            String companyId,
            Long workspaceId,
            OntologyDocument document) {
        Map<Long, String> conceptKeys = new HashMap<>();
        concepts.findByWorkspaceIdAndCompanyIdOrderByIdAsc(workspaceId, companyId)
                .forEach(concept -> conceptKeys.put(concept.getId(), concept.getKey()));
        Map<String, String> existingEndpoints = new HashMap<>();
        relations.findByWorkspaceIdAndCompanyIdOrderByIdAsc(workspaceId, companyId)
                .forEach(relation -> existingEndpoints.put(
                        relation.getKey(),
                        conceptKeys.get(relation.getSourceConceptId())
                                + "\u0000"
                                + conceptKeys.get(relation.getTargetConceptId())));
        Set<String> changed = new LinkedHashSet<>();
        for (OntologyDocument.Relation relation : safe(document.relations())) {
            String previous = existingEndpoints.get(relation.key());
            if (previous != null
                    && !previous.equals(
                    relation.sourceConceptKey() + "\u0000" + relation.targetConceptKey())) {
                changed.add(relation.key());
            }
        }
        return Set.copyOf(changed);
    }

    private String mappingIdentity(String targetType, String targetKey, Long dataSourceId) {
        return normalizedTargetType(targetType) + "\u0000" + targetKey + "\u0000" + dataSourceId;
    }

    private String normalizedTargetType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            throw new IllegalArgumentException("MAPPING_TARGET_TYPE_REQUIRED");
        }
        return targetType.trim().toUpperCase(Locale.ROOT);
    }

    private String serverOrigin(String requested) {
        if ("AI".equalsIgnoreCase(requested)) {
            return "AI";
        }
        if ("REFERENCE".equalsIgnoreCase(requested)) {
            return "REFERENCE";
        }
        return "MANUAL";
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

    private OntologyWorkspaceEntity requireWorkspace(String companyId, Long workspaceId) {
        return workspaces.findForUpdateByIdAndCompanyId(workspaceId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Ontology workspace not found"));
    }

    private void requireCurrentOrg(String companyId) {
        if (!Objects.equals(TenantContext.requireCompanyId(), companyId)) {
            throw new ForbiddenException("Ontology company does not match current company");
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

    private record SavedDataSources(Map<Long, Long> ids, Set<Long> changedIds) {
    }
}
