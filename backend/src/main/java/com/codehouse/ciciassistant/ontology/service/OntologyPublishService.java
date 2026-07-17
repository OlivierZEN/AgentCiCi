package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OntologyPublishService {

    private final OntologyWorkspaceRepository workspaces;
    private final OntologyDraftService drafts;
    private final OntologyValidationService validation;
    private final OntologyCompilerService compiler;
    private final OntologyTenantPersistence persistence;
    private final OntologyVersionRepository versions;
    private final OntologyMappingRepository mappings;
    private final OntologyMappingIntegrityService mappingIntegrity;
    private final ObjectMapper objectMapper;

    public OntologyPublishService(
            OntologyWorkspaceRepository workspaces,
            OntologyDraftService drafts,
            OntologyValidationService validation,
            OntologyCompilerService compiler,
            OntologyTenantPersistence persistence,
            OntologyVersionRepository versions,
            OntologyMappingRepository mappings,
            OntologyMappingIntegrityService mappingIntegrity,
            ObjectMapper objectMapper) {
        this.workspaces = workspaces;
        this.drafts = drafts;
        this.validation = validation;
        this.compiler = compiler;
        this.persistence = persistence;
        this.versions = versions;
        this.mappings = mappings;
        this.mappingIntegrity = mappingIntegrity;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OntologyVersionEntity publish(
            String orgId,
            String userId,
            Long workspaceId,
            Long expectedRevision) {
        requireCurrentOrg(orgId);
        requireCurrentHuman(userId);
        OntologyWorkspaceEntity workspace = workspaces.findForUpdateByIdAndOrgId(workspaceId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Ontology workspace not found"));
        if (!Objects.equals(workspace.getDraftRevision(), expectedRevision)) {
            throw new ConflictException("ONTOLOGY_REVISION_CONFLICT");
        }
        if ("ARCHIVED".equals(workspace.getStatus())) {
            throw new ConflictException("ONTOLOGY_WORKSPACE_ARCHIVED");
        }
        OntologyVersionEntity existingVersion = versions
                .findByWorkspaceIdAndOrgIdAndSourceDraftRevision(
                        workspaceId,
                        orgId,
                        workspace.getDraftRevision())
                .orElse(null);
        if (existingVersion != null) {
            return existingVersion;
        }

        OntologyDocument document = drafts.loadDraft(orgId, workspaceId, workspace);
        List<OntologyValidationService.ValidationIssue> issues = validation.validate(document, true);
        if (issues.stream().anyMatch(issue ->
                issue.severity() == OntologyValidationService.Severity.ERROR)) {
            throw new ConflictException("ONTOLOGY_VALIDATION_FAILED");
        }
        requireFreshServerMappings(orgId, workspaceId, document);

        int nextVersion = workspace.getPublishedVersion() == null
                ? 1 : workspace.getPublishedVersion() + 1;
        OntologyCompilerService.CompiledContracts contracts = compiler.compile(document, nextVersion);
        OntologyVersionEntity version = new OntologyVersionEntity(
                orgId,
                workspaceId,
                nextVersion,
                workspace.getDraftRevision(),
                contracts.contentHash(),
                contracts.snapshotJson(),
                contracts.jsonSchema(),
                contracts.graphqlSdl(),
                contracts.queryContractJson(),
                writeJson(issues),
                userId);
        OntologyVersionEntity savedVersion = persistence.saveForCurrentOrg(version);
        workspace.markPublished(nextVersion, userId);
        persistence.saveForCurrentOrg(workspace);
        return savedVersion;
    }

    private void requireFreshServerMappings(
            String orgId,
            Long workspaceId,
            OntologyDocument document) {
        for (OntologyDocument.Mapping mapping : document.mappings() == null
                ? List.<OntologyDocument.Mapping>of()
                : document.mappings()) {
            String targetType = mapping.targetType() == null
                    ? ""
                    : mapping.targetType().trim().toUpperCase(java.util.Locale.ROOT);
            OntologyMappingEntity stored = mappings
                    .findByWorkspaceIdAndOrgIdAndTargetTypeAndTargetKeyAndDataSourceId(
                            workspaceId,
                            orgId,
                            targetType,
                            mapping.targetKey(),
                            mapping.dataSourceId())
                    .orElseThrow(() -> new ConflictException(
                            "ONTOLOGY_MAPPING_VALIDATION_REQUIRED"));
            if (!stored.definitionMatches(
                    mapping.physicalObjectKey(),
                    mapping.physicalFieldKey(),
                    mapping.relationTargetFieldKey(),
                    mapping.transform(),
                    java.math.BigDecimal.valueOf(mapping.confidence()))
                    || !"VALID".equals(stored.getValidationStatus())
                    || stored.getLastValidatedAt() == null
                    || !mappingIntegrity.validate(orgId, workspaceId, document, mapping).valid()
                    || !mappingIntegrity.isFresh(
                    orgId,
                    workspaceId,
                    document,
                    mapping,
                    stored.getLastValidatedAt())) {
                throw new ConflictException("ONTOLOGY_MAPPING_VALIDATION_REQUIRED");
            }
        }
    }

    private void requireCurrentOrg(String orgId) {
        if (!Objects.equals(TenantContext.requireOrgId(), orgId)) {
            throw new ForbiddenException("Ontology organization does not match current organization");
        }
    }

    private void requireCurrentHuman(String userId) {
        if (userId == null
                || userId.isBlank()
                || TenantContext.getUserId().filter(userId::equals).isEmpty()) {
            throw new ForbiddenException("ONTOLOGY_PUBLISH_REQUIRES_HUMAN");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize ontology validation summary", exception);
        }
    }
}
