package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
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
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OntologyCatalogTransactionService {

    private final OntologyWorkspaceRepository workspaces;
    private final OntologyDataSourceRepository dataSources;
    private final OntologyPhysicalObjectRepository objects;
    private final OntologyPhysicalFieldRepository fields;
    private final OntologyMappingRepository mappings;
    private final OntologyTenantPersistence persistence;
    private final OntologyDraftService drafts;
    private final OntologyMappingIntegrityService integrity;
    private final OntologyDataSourcePolicy dataSourcePolicy;

    public OntologyCatalogTransactionService(
            OntologyWorkspaceRepository workspaces,
            OntologyDataSourceRepository dataSources,
            OntologyPhysicalObjectRepository objects,
            OntologyPhysicalFieldRepository fields,
            OntologyMappingRepository mappings,
            OntologyTenantPersistence persistence,
            OntologyDraftService drafts,
            OntologyMappingIntegrityService integrity,
            OntologyDataSourcePolicy dataSourcePolicy) {
        this.workspaces = workspaces;
        this.dataSources = dataSources;
        this.objects = objects;
        this.fields = fields;
        this.mappings = mappings;
        this.persistence = persistence;
        this.drafts = drafts;
        this.integrity = integrity;
        this.dataSourcePolicy = dataSourcePolicy;
    }

    @Transactional(readOnly = true)
    public SourcePreparation prepareSource(
            String orgId,
            Long workspaceId,
            Long dataSourceId,
            Long expectedRevision,
            String objectKey) {
        requireCurrentOrg(orgId);
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId, orgId);
        verifyWorkspace(workspace, expectedRevision);
        OntologyDataSourceEntity source = dataSources
                .findByIdAndWorkspaceIdAndOrgId(dataSourceId, workspaceId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("ONTOLOGY_DATA_SOURCE_NOT_FOUND"));
        Long objectId = null;
        if (objectKey != null) {
            List<OntologyPhysicalObjectEntity> selected = objects
                    .findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                            dataSourceId, workspaceId, orgId).stream()
                    .filter(candidate -> Objects.equals(candidate.getObjectKey(), objectKey))
                    .toList();
            if (selected.size() != 1) {
                throw new ResourceNotFoundException("CATALOG_OBJECT_NOT_FOUND");
            }
            objectId = selected.getFirst().getId();
        }
        return new SourcePreparation(
                orgId,
                workspaceId,
                expectedRevision,
                toConfig(source),
                objectKey,
                objectId);
    }

    @Transactional
    public long commitObjects(
            SourcePreparation prepared,
            String userId,
            List<PhysicalObject> discovered) {
        OntologyWorkspaceEntity workspace = lockPrepared(prepared);
        requireSourceUnchanged(prepared);
        Map<String, OntologyPhysicalObjectEntity> existing = objects
                .findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                        prepared.source().id(), prepared.workspaceId(), prepared.orgId()).stream()
                .collect(Collectors.toMap(
                        OntologyPhysicalObjectEntity::getObjectKey,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<String> discoveredKeys = new LinkedHashSet<>();
        for (PhysicalObject value : discovered) {
            discoveredKeys.add(value.key());
            OntologyPhysicalObjectEntity entity = existing.get(value.key());
            if (entity == null) {
                entity = new OntologyPhysicalObjectEntity(
                        prepared.orgId(),
                        prepared.workspaceId(),
                        prepared.source().id(),
                        value.key(),
                        value.name(),
                        value.objectType(),
                        value.metadataJson());
            } else {
                entity.refresh(value.name(), value.objectType(), value.metadataJson());
            }
            persistence.saveForCurrentOrg(entity);
        }
        existing.values().stream()
                .filter(entity -> !discoveredKeys.contains(entity.getObjectKey()))
                .forEach(entity -> persistence.deleteForCurrentOrg(
                        prepared.orgId(),
                        () -> objects.deleteByIdAndWorkspaceIdAndOrgId(
                                entity.getId(), prepared.workspaceId(), prepared.orgId())));
        invalidateSourceMappings(prepared.orgId(), prepared.workspaceId(), prepared.source().id());
        workspace.advanceDraftRevision(userId);
        persistence.saveForCurrentOrg(workspace);
        return workspace.getDraftRevision();
    }

    @Transactional
    public long commitFields(
            SourcePreparation prepared,
            String userId,
            List<PhysicalField> discovered) {
        OntologyWorkspaceEntity workspace = lockPrepared(prepared);
        requireSourceUnchanged(prepared);
        OntologyPhysicalObjectEntity object = objects
                .findByIdAndWorkspaceIdAndOrgId(
                        prepared.objectId(), prepared.workspaceId(), prepared.orgId())
                .filter(candidate -> Objects.equals(
                        candidate.getObjectKey(), prepared.objectKey()))
                .orElseThrow(() -> new ConflictException("ONTOLOGY_REVISION_CONFLICT"));
        Map<String, OntologyPhysicalFieldEntity> existing = fields
                .findByPhysicalObjectIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                        object.getId(), prepared.workspaceId(), prepared.orgId()).stream()
                .collect(Collectors.toMap(
                        OntologyPhysicalFieldEntity::getFieldKey,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<String> discoveredKeys = new LinkedHashSet<>();
        for (PhysicalField value : discovered) {
            discoveredKeys.add(value.key());
            OntologyPhysicalFieldEntity entity = existing.get(value.key());
            if (entity == null) {
                entity = new OntologyPhysicalFieldEntity(
                        prepared.orgId(),
                        prepared.workspaceId(),
                        object.getId(),
                        value.key(),
                        value.name(),
                        value.dataType(),
                        value.nullable(),
                        value.multiple(),
                        value.metadataJson());
            } else {
                entity.refresh(
                        value.name(),
                        value.dataType(),
                        value.nullable(),
                        value.multiple(),
                        value.metadataJson());
            }
            persistence.saveForCurrentOrg(entity);
        }
        existing.values().stream()
                .filter(entity -> !discoveredKeys.contains(entity.getFieldKey()))
                .forEach(entity -> persistence.deleteForCurrentOrg(
                        prepared.orgId(),
                        () -> fields.deleteByIdAndWorkspaceIdAndOrgId(
                                entity.getId(), prepared.workspaceId(), prepared.orgId())));
        invalidateSourceMappings(prepared.orgId(), prepared.workspaceId(), prepared.source().id());
        workspace.advanceDraftRevision(userId);
        persistence.saveForCurrentOrg(workspace);
        return workspace.getDraftRevision();
    }

    @Transactional(readOnly = true)
    public MappingPreparation prepareMapping(
            String orgId,
            Long workspaceId,
            Long expectedRevision,
            MappingKey key) {
        requireCurrentOrg(orgId);
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId, orgId);
        verifyWorkspace(workspace, expectedRevision);
        OntologyMappingEntity mapping = mappings
                .findByWorkspaceIdAndOrgIdAndTargetTypeAndTargetKeyAndDataSourceId(
                        workspaceId,
                        orgId,
                        key.targetType(),
                        key.targetKey(),
                        key.dataSourceId())
                .orElseThrow(() -> new ResourceNotFoundException("ONTOLOGY_MAPPING_NOT_FOUND"));
        OntologyDataSourceEntity source = dataSources
                .findByIdAndWorkspaceIdAndOrgId(
                        key.dataSourceId(), workspaceId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("ONTOLOGY_DATA_SOURCE_NOT_FOUND"));
        return new MappingPreparation(
                orgId,
                workspaceId,
                expectedRevision,
                key,
                toConfig(source),
                toDocument(mapping));
    }

    @Transactional
    public MappingCommit commitMappingValidation(
            MappingPreparation prepared,
            String userId,
            MappingValidation adapterValidation) {
        MappingBatchCommit committed = commitMappingValidationsInternal(
                List.of(prepared),
                userId,
                java.util.Collections.singletonList(adapterValidation));
        return new MappingCommit(committed.validations().getFirst(), committed.revision());
    }

    @Transactional
    public MappingBatchCommit commitMappingValidations(
            List<MappingPreparation> preparedMappings,
            String userId,
            List<MappingValidation> adapterValidations) {
        return commitMappingValidationsInternal(
                preparedMappings, userId, adapterValidations);
    }

    private MappingBatchCommit commitMappingValidationsInternal(
            List<MappingPreparation> preparedMappings,
            String userId,
            List<MappingValidation> adapterValidations) {
        if (preparedMappings == null
                || preparedMappings.isEmpty()
                || adapterValidations == null
                || preparedMappings.size() != adapterValidations.size()) {
            throw new IllegalArgumentException("MAPPING_IDENTITY_REQUIRED");
        }
        MappingPreparation first = preparedMappings.getFirst();
        Set<MappingKey> identities = new LinkedHashSet<>();
        for (MappingPreparation prepared : preparedMappings) {
            if (prepared == null
                    || !Objects.equals(prepared.orgId(), first.orgId())
                    || !Objects.equals(prepared.workspaceId(), first.workspaceId())
                    || !Objects.equals(prepared.expectedRevision(), first.expectedRevision())
                    || !identities.add(prepared.key())) {
                throw new IllegalArgumentException("MAPPING_IDENTITY_REQUIRED");
            }
        }
        OntologyWorkspaceEntity workspace = workspaces
                .findForUpdateByIdAndOrgId(first.workspaceId(), first.orgId())
                .orElseThrow(() -> new ResourceNotFoundException("ONTOLOGY_NOT_FOUND"));
        verifyWorkspace(workspace, first.expectedRevision());
        OntologyDocument document = drafts.loadDraft(
                first.orgId(), first.workspaceId(), workspace);
        List<MappingValidation> finalValidations = new java.util.ArrayList<>();
        for (int index = 0; index < preparedMappings.size(); index++) {
            MappingPreparation prepared = preparedMappings.get(index);
            requireSourceUnchanged(new SourcePreparation(
                    prepared.orgId(),
                    prepared.workspaceId(),
                    prepared.expectedRevision(),
                    prepared.source(),
                    null,
                    null));
            OntologyMappingEntity mapping = mappings
                    .findByWorkspaceIdAndOrgIdAndTargetTypeAndTargetKeyAndDataSourceId(
                            prepared.workspaceId(),
                            prepared.orgId(),
                            prepared.key().targetType(),
                            prepared.key().targetKey(),
                            prepared.key().dataSourceId())
                    .orElseThrow(() -> new ConflictException("ONTOLOGY_REVISION_CONFLICT"));
            if (!sameDefinition(prepared.mapping(), toDocument(mapping))) {
                throw new ConflictException("ONTOLOGY_REVISION_CONFLICT");
            }
            MappingValidation adapterValidation = adapterValidations.get(index);
            MappingValidation integrityValidation = integrity.validate(
                    prepared.orgId(), prepared.workspaceId(), document, prepared.mapping());
            MappingValidation finalValidation = adapterValidation != null
                    && adapterValidation.valid()
                    && integrityValidation.valid()
                    ? MappingValidation.success()
                    : adapterValidation == null || adapterValidation.valid()
                            ? integrityValidation
                            : adapterValidation;
            mapping.applyValidation(finalValidation.valid());
            persistence.saveForCurrentOrg(mapping);
            finalValidations.add(finalValidation);
        }
        workspace.advanceDraftRevision(userId);
        persistence.saveForCurrentOrg(workspace);
        return new MappingBatchCommit(List.copyOf(finalValidations), workspace.getDraftRevision());
    }

    private OntologyWorkspaceEntity lockPrepared(SourcePreparation prepared) {
        OntologyWorkspaceEntity workspace = workspaces
                .findForUpdateByIdAndOrgId(prepared.workspaceId(), prepared.orgId())
                .orElseThrow(() -> new ResourceNotFoundException("ONTOLOGY_NOT_FOUND"));
        verifyWorkspace(workspace, prepared.expectedRevision());
        return workspace;
    }

    private void requireSourceUnchanged(SourcePreparation prepared) {
        DataSourceConfig current = dataSources
                .findByIdAndWorkspaceIdAndOrgId(
                        prepared.source().id(), prepared.workspaceId(), prepared.orgId())
                .map(this::toConfig)
                .orElseThrow(() -> new ConflictException("ONTOLOGY_REVISION_CONFLICT"));
        if (!Objects.equals(current, prepared.source())) {
            throw new ConflictException("ONTOLOGY_REVISION_CONFLICT");
        }
    }

    private void invalidateSourceMappings(String orgId, Long workspaceId, Long dataSourceId) {
        mappings.findByWorkspaceIdAndOrgIdOrderByIdAsc(workspaceId, orgId).stream()
                .filter(mapping -> Objects.equals(mapping.getDataSourceId(), dataSourceId))
                .forEach(mapping -> {
                    mapping.markPending();
                    persistence.saveForCurrentOrg(mapping);
                });
    }

    private OntologyWorkspaceEntity requireWorkspace(Long workspaceId, String orgId) {
        return workspaces.findByIdAndOrgId(workspaceId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("ONTOLOGY_NOT_FOUND"));
    }

    private void verifyWorkspace(OntologyWorkspaceEntity workspace, Long expectedRevision) {
        if ("ARCHIVED".equals(workspace.getStatus())) {
            throw new ConflictException("ONTOLOGY_WORKSPACE_ARCHIVED");
        }
        if (expectedRevision == null
                || !Objects.equals(workspace.getDraftRevision(), expectedRevision)) {
            throw new ConflictException("ONTOLOGY_REVISION_CONFLICT");
        }
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
                dataSourcePolicy.adapterKey(source.getConfigJson()),
                source.getConfigJson(),
                source.getSampleDataJson());
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

    private boolean sameDefinition(
            OntologyDocument.Mapping left,
            OntologyDocument.Mapping right) {
        return Objects.equals(left.targetType(), right.targetType())
                && Objects.equals(left.targetKey(), right.targetKey())
                && Objects.equals(left.dataSourceId(), right.dataSourceId())
                && Objects.equals(left.physicalObjectKey(), right.physicalObjectKey())
                && Objects.equals(left.physicalFieldKey(), right.physicalFieldKey())
                && Objects.equals(left.relationTargetFieldKey(), right.relationTargetFieldKey())
                && Objects.equals(left.transform(), right.transform())
                && Double.compare(left.confidence(), right.confidence()) == 0;
    }

    private void requireCurrentOrg(String orgId) {
        if (!Objects.equals(TenantContext.requireOrgId(), orgId)) {
            throw new ResourceNotFoundException("ONTOLOGY_NOT_FOUND");
        }
    }

    public record SourcePreparation(
            String orgId,
            Long workspaceId,
            Long expectedRevision,
            DataSourceConfig source,
            String objectKey,
            Long objectId) {
    }

    public record MappingPreparation(
            String orgId,
            Long workspaceId,
            Long expectedRevision,
            MappingKey key,
            DataSourceConfig source,
            OntologyDocument.Mapping mapping) {
    }

    public record MappingKey(String targetType, String targetKey, Long dataSourceId) {
        public MappingKey {
            targetType = targetType == null
                    ? ""
                    : targetType.trim().toUpperCase(Locale.ROOT);
            if (targetType.isBlank() || targetKey == null || targetKey.isBlank() || dataSourceId == null) {
                throw new IllegalArgumentException("MAPPING_IDENTITY_REQUIRED");
            }
        }
    }

    public record MappingCommit(MappingValidation validation, long revision) {
    }

    public record MappingBatchCommit(List<MappingValidation> validations, long revision) {
        public MappingBatchCommit {
            validations = validations == null ? List.of() : List.copyOf(validations);
        }
    }
}
