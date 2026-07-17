package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.MappingValidation;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class OntologyMappingIntegrityService {

    private final OntologyPhysicalObjectRepository objects;
    private final OntologyPhysicalFieldRepository fields;

    public OntologyMappingIntegrityService(
            OntologyPhysicalObjectRepository objects,
            OntologyPhysicalFieldRepository fields) {
        this.objects = objects;
        this.fields = fields;
    }

    public MappingValidation validate(
            String orgId,
            Long workspaceId,
            OntologyDocument document,
            OntologyDocument.Mapping mapping) {
        if (mapping == null || mapping.dataSourceId() == null) {
            return invalid("MAPPING_INVALID", "Mapping is required");
        }
        List<OntologyPhysicalObjectEntity> mappedObjects = objects
                .findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                        mapping.dataSourceId(), workspaceId, orgId).stream()
                .filter(candidate -> Objects.equals(
                        candidate.getObjectKey(), mapping.physicalObjectKey()))
                .toList();
        if (mappedObjects.size() != 1) {
            return invalid("PHYSICAL_OBJECT_NOT_FOUND", "Mapped object was not discovered");
        }
        OntologyPhysicalObjectEntity sourceObject = mappedObjects.getFirst();
        String targetType = normalized(mapping.targetType());
        if (!"CONCEPT".equals(targetType)
                && !fieldExists(orgId, workspaceId, sourceObject, mapping.physicalFieldKey())) {
            return invalid("PHYSICAL_FIELD_NOT_FOUND", "Mapped field was not discovered");
        }
        if (!"RELATION".equals(targetType)) {
            return mapping.relationTargetFieldKey() == null
                    ? MappingValidation.success()
                    : invalid("RELATION_TARGET_FIELD_NOT_ALLOWED", "Only relation mappings have a target field");
        }

        List<OntologyDocument.Relation> relations = safe(document.relations()).stream()
                .filter(candidate -> Objects.equals(candidate.key(), mapping.targetKey()))
                .toList();
        if (relations.size() != 1) {
            return invalid("RELATION_NOT_FOUND", "Mapped relation was not found");
        }
        OntologyDocument.Relation relation = relations.getFirst();
        List<OntologyDocument.Mapping> sourceConcepts = conceptMappings(
                document, relation.sourceConceptKey(), mapping.dataSourceId());
        List<OntologyDocument.Mapping> targetConcepts = conceptMappings(
                document, relation.targetConceptKey(), mapping.dataSourceId());
        if (sourceConcepts.size() != 1 || targetConcepts.size() != 1) {
            return invalid("RELATION_CONCEPT_MAPPING_INCOMPLETE", "Both relation concepts require one mapping");
        }
        if (!Objects.equals(
                mapping.physicalObjectKey(), sourceConcepts.getFirst().physicalObjectKey())) {
            return invalid("RELATION_SOURCE_OBJECT_MISMATCH", "Relation source object does not match its concept");
        }
        List<OntologyPhysicalObjectEntity> targetObjects = objects
                .findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                        mapping.dataSourceId(), workspaceId, orgId).stream()
                .filter(candidate -> Objects.equals(
                        candidate.getObjectKey(), targetConcepts.getFirst().physicalObjectKey()))
                .toList();
        if (targetObjects.size() != 1
                || !fieldExists(
                orgId,
                workspaceId,
                targetObjects.getFirst(),
                mapping.relationTargetFieldKey())) {
            return invalid("RELATION_TARGET_FIELD_NOT_FOUND", "Relation target field was not discovered");
        }
        return MappingValidation.success();
    }

    public boolean isFresh(
            String orgId,
            Long workspaceId,
            OntologyDocument document,
            OntologyDocument.Mapping mapping,
            Instant validatedAt) {
        if (validatedAt == null) {
            return false;
        }
        OntologyPhysicalObjectEntity sourceObject = uniqueObject(
                orgId, workspaceId, mapping.dataSourceId(), mapping.physicalObjectKey());
        if (sourceObject == null || validatedAt.isBefore(sourceObject.getDiscoveredAt())) {
            return false;
        }
        if (mapping.physicalFieldKey() != null) {
            OntologyPhysicalFieldEntity field = uniqueField(
                    orgId, workspaceId, sourceObject, mapping.physicalFieldKey());
            if (field == null || validatedAt.isBefore(field.getDiscoveredAt())) {
                return false;
            }
        }
        if (!"RELATION".equals(normalized(mapping.targetType()))) {
            return true;
        }
        OntologyDocument.Relation relation = safe(document.relations()).stream()
                .filter(candidate -> Objects.equals(candidate.key(), mapping.targetKey()))
                .findFirst()
                .orElse(null);
        if (relation == null) {
            return false;
        }
        List<OntologyDocument.Mapping> targetConcepts = conceptMappings(
                document, relation.targetConceptKey(), mapping.dataSourceId());
        if (targetConcepts.size() != 1) {
            return false;
        }
        OntologyPhysicalObjectEntity targetObject = uniqueObject(
                orgId,
                workspaceId,
                mapping.dataSourceId(),
                targetConcepts.getFirst().physicalObjectKey());
        OntologyPhysicalFieldEntity targetField = targetObject == null
                ? null
                : uniqueField(
                        orgId,
                        workspaceId,
                        targetObject,
                        mapping.relationTargetFieldKey());
        return targetObject != null
                && targetField != null
                && !validatedAt.isBefore(targetObject.getDiscoveredAt())
                && !validatedAt.isBefore(targetField.getDiscoveredAt());
    }

    private List<OntologyDocument.Mapping> conceptMappings(
            OntologyDocument document,
            String conceptKey,
            Long dataSourceId) {
        return safe(document.mappings()).stream()
                .filter(candidate -> "CONCEPT".equals(normalized(candidate.targetType())))
                .filter(candidate -> Objects.equals(candidate.targetKey(), conceptKey))
                .filter(candidate -> Objects.equals(candidate.dataSourceId(), dataSourceId))
                .toList();
    }

    private boolean fieldExists(
            String orgId,
            Long workspaceId,
            OntologyPhysicalObjectEntity object,
            String fieldKey) {
        return uniqueField(orgId, workspaceId, object, fieldKey) != null;
    }

    private OntologyPhysicalObjectEntity uniqueObject(
            String orgId,
            Long workspaceId,
            Long dataSourceId,
            String objectKey) {
        List<OntologyPhysicalObjectEntity> matches = objects
                .findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                        dataSourceId, workspaceId, orgId).stream()
                .filter(candidate -> Objects.equals(candidate.getObjectKey(), objectKey))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private OntologyPhysicalFieldEntity uniqueField(
            String orgId,
            Long workspaceId,
            OntologyPhysicalObjectEntity object,
            String fieldKey) {
        if (fieldKey == null || fieldKey.isBlank()) {
            return null;
        }
        List<OntologyPhysicalFieldEntity> matches = fields
                .findByPhysicalObjectIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                        object.getId(), workspaceId, orgId).stream()
                .filter(candidate -> Objects.equals(candidate.getFieldKey(), fieldKey))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private MappingValidation invalid(String code, String message) {
        return MappingValidation.invalid(code, message);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
