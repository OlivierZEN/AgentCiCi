package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.AdapterContext;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.DataSourceConfig;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.MappingValidation;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalField;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalObject;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OntologyCatalogService {

    private final OntologyDataSourceRepository dataSources;
    private final OntologyPhysicalObjectRepository objects;
    private final OntologyPhysicalFieldRepository fields;
    private final OntologyMappingRepository mappings;
    private final OntologyTenantPersistence persistence;
    private final List<OntologyDataSourceAdapter> adapters;
    private final ObjectMapper objectMapper;

    public OntologyCatalogService(
            OntologyDataSourceRepository dataSources,
            OntologyPhysicalObjectRepository objects,
            OntologyPhysicalFieldRepository fields,
            OntologyMappingRepository mappings,
            OntologyTenantPersistence persistence,
            List<OntologyDataSourceAdapter> adapters,
            ObjectMapper objectMapper) {
        this.dataSources = dataSources;
        this.objects = objects;
        this.fields = fields;
        this.mappings = mappings;
        this.persistence = persistence;
        this.adapters = List.copyOf(adapters);
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<PhysicalObject> discoverObjects(
            String orgId,
            String userId,
            Long workspaceId,
            Long dataSourceId) {
        requireCurrentContext(orgId, userId);
        OntologyDataSourceEntity sourceEntity = requireSource(
                orgId, workspaceId, dataSourceId);
        DataSourceConfig source = toConfig(sourceEntity);
        OntologyDataSourceAdapter adapter = requireAdapter(source);
        List<PhysicalObject> discovered = List.copyOf(adapter.discoverObjects(
                new AdapterContext(orgId, userId), source));
        requireUniqueKeys(discovered.stream().map(PhysicalObject::key).toList());

        Map<String, OntologyPhysicalObjectEntity> existing = objects
                .findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                        dataSourceId, workspaceId, orgId).stream()
                .collect(Collectors.toMap(
                        OntologyPhysicalObjectEntity::getObjectKey,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<String> discoveredKeys = new LinkedHashSet<>();
        for (PhysicalObject physicalObject : discovered) {
            requireCatalogText(physicalObject.key(), "CATALOG_OBJECT_KEY_REQUIRED");
            requireCatalogText(physicalObject.name(), "CATALOG_OBJECT_NAME_REQUIRED");
            discoveredKeys.add(physicalObject.key());
            OntologyPhysicalObjectEntity entity = existing.get(physicalObject.key());
            if (entity == null) {
                entity = new OntologyPhysicalObjectEntity(
                        orgId,
                        workspaceId,
                        dataSourceId,
                        physicalObject.key(),
                        physicalObject.name(),
                        physicalObject.objectType(),
                        physicalObject.metadataJson());
            } else {
                entity.refresh(
                        physicalObject.name(),
                        physicalObject.objectType(),
                        physicalObject.metadataJson());
            }
            persistence.saveForCurrentOrg(entity);
        }
        existing.values().stream()
                .filter(entity -> !discoveredKeys.contains(entity.getObjectKey()))
                .forEach(entity -> persistence.deleteForCurrentOrg(
                        orgId,
                        () -> objects.deleteByIdAndWorkspaceIdAndOrgId(
                                entity.getId(), workspaceId, orgId)));
        return discovered;
    }

    @Transactional
    public List<PhysicalField> discoverFields(
            String orgId,
            String userId,
            Long workspaceId,
            Long dataSourceId,
            String objectKey) {
        requireCurrentContext(orgId, userId);
        OntologyDataSourceEntity sourceEntity = requireSource(
                orgId, workspaceId, dataSourceId);
        OntologyPhysicalObjectEntity object = objects
                .findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                        dataSourceId, workspaceId, orgId).stream()
                .filter(candidate -> Objects.equals(candidate.getObjectKey(), objectKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("CATALOG_OBJECT_NOT_FOUND"));
        DataSourceConfig source = toConfig(sourceEntity);
        OntologyDataSourceAdapter adapter = requireAdapter(source);
        List<PhysicalField> discovered = List.copyOf(adapter.discoverFields(
                new AdapterContext(orgId, userId), source, objectKey));
        requireUniqueKeys(discovered.stream().map(PhysicalField::key).toList());

        Map<String, OntologyPhysicalFieldEntity> existing = fields
                .findByPhysicalObjectIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                        object.getId(), workspaceId, orgId).stream()
                .collect(Collectors.toMap(
                        OntologyPhysicalFieldEntity::getFieldKey,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<String> discoveredKeys = new LinkedHashSet<>();
        for (PhysicalField physicalField : discovered) {
            requireCatalogText(physicalField.key(), "CATALOG_FIELD_KEY_REQUIRED");
            requireCatalogText(physicalField.name(), "CATALOG_FIELD_NAME_REQUIRED");
            discoveredKeys.add(physicalField.key());
            OntologyPhysicalFieldEntity entity = existing.get(physicalField.key());
            if (entity == null) {
                entity = new OntologyPhysicalFieldEntity(
                        orgId,
                        workspaceId,
                        object.getId(),
                        physicalField.key(),
                        physicalField.name(),
                        physicalField.dataType(),
                        physicalField.nullable(),
                        physicalField.multiple(),
                        physicalField.metadataJson());
            } else {
                entity.refresh(
                        physicalField.name(),
                        physicalField.dataType(),
                        physicalField.nullable(),
                        physicalField.multiple(),
                        physicalField.metadataJson());
            }
            persistence.saveForCurrentOrg(entity);
        }
        existing.values().stream()
                .filter(entity -> !discoveredKeys.contains(entity.getFieldKey()))
                .forEach(entity -> persistence.deleteForCurrentOrg(
                        orgId,
                        () -> fields.deleteByIdAndWorkspaceIdAndOrgId(
                                entity.getId(), workspaceId, orgId)));
        return discovered;
    }

    @Transactional
    public MappingValidation validateMapping(
            String orgId,
            String userId,
            Long workspaceId,
            Long mappingId) {
        requireCurrentContext(orgId, userId);
        OntologyMappingEntity mapping = mappings.findByIdAndWorkspaceIdAndOrgId(
                        mappingId, workspaceId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_MAPPING_NOT_FOUND"));
        OntologyDataSourceEntity sourceEntity = requireSource(
                orgId, workspaceId, mapping.getDataSourceId());
        DataSourceConfig source = toConfig(sourceEntity);
        MappingValidation validation = requireAdapter(source).validateMapping(
                new AdapterContext(orgId, userId),
                source,
                toDocument(mapping));
        if (validation == null) {
            throw new IllegalStateException("MAPPING_VALIDATION_RESULT_REQUIRED");
        }
        mapping.applyValidation(validation.valid());
        persistence.saveForCurrentOrg(mapping);
        return validation;
    }

    private OntologyDataSourceEntity requireSource(
            String orgId,
            Long workspaceId,
            Long dataSourceId) {
        return dataSources.findByIdAndWorkspaceIdAndOrgId(
                        dataSourceId, workspaceId, orgId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ONTOLOGY_DATA_SOURCE_NOT_FOUND"));
    }

    private DataSourceConfig toConfig(OntologyDataSourceEntity source) {
        OntologyDocument.SourceType sourceType;
        try {
            sourceType = OntologyDocument.SourceType.valueOf(source.getSourceType());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("ONTOLOGY_SOURCE_TYPE_INVALID", exception);
        }
        return new DataSourceConfig(
                source.getId(),
                source.getWorkspaceId(),
                source.getKey(),
                source.getName(),
                sourceType,
                adapterKey(source.getConfigJson()),
                source.getConfigJson(),
                source.getSampleDataJson());
    }

    private String adapterKey(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return null;
        }
        try {
            String value = objectMapper.readTree(configJson).path("adapterKey").asText("");
            return value.isBlank() ? null : value;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("DATA_SOURCE_CONFIG_INVALID", exception);
        }
    }

    private OntologyDataSourceAdapter requireAdapter(DataSourceConfig source) {
        return adapters.stream()
                .filter(adapter -> adapter.supports(source))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ONTOLOGY_ADAPTER_NOT_AVAILABLE"));
    }

    private OntologyDocument.Mapping toDocument(OntologyMappingEntity mapping) {
        return new OntologyDocument.Mapping(
                mapping.getTargetType(),
                mapping.getTargetKey(),
                mapping.getDataSourceId(),
                mapping.getPhysicalObjectKey(),
                mapping.getPhysicalFieldKey(),
                mapping.getRelationTargetFieldKey(),
                mapping.getTransform(),
                mapping.getConfidence().doubleValue(),
                mapping.getSource(),
                mapping.getValidationStatus());
    }

    private void requireCurrentContext(String orgId, String userId) {
        if (userId == null
                || userId.isBlank()
                || !Objects.equals(TenantContext.requireOrgId(), orgId)
                || TenantContext.getUserId().filter(userId::equals).isEmpty()) {
            throw new ForbiddenException("ONTOLOGY_CATALOG_CONTEXT_MISMATCH");
        }
    }

    private void requireUniqueKeys(List<String> keys) {
        if (new LinkedHashSet<>(keys).size() != keys.size()) {
            throw new IllegalArgumentException("CATALOG_DUPLICATE_KEY");
        }
    }

    private void requireCatalogText(String value, String errorCode) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorCode);
        }
    }
}
