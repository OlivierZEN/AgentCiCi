package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.MappingValidation;
import com.codehouse.ciciassistant.ontology.domain.OntologyAiProposalEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyAiProposalRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.ontology.service.OntologyAiProposalService.ProposalCommand;
import com.codehouse.ciciassistant.ontology.service.OntologyAiProposalService.ProposalDiff;
import com.codehouse.ciciassistant.ontology.service.OntologyAiProposalService.ProposalView;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogService.CatalogMutation;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.MappingBatchCommit;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.MappingKey;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OntologyManagementService {

    private static final Pattern KEY_PATTERN = Pattern.compile(
            "^[a-z][a-z0-9]*(?:[-_][a-z0-9]+)*$");
    private static final String WORKSPACE_KEY_UNIQUE_CONSTRAINT =
            "uq_ontology_workspace_org_key";

    private final OntologyWorkspaceRepository workspaces;
    private final OntologyDataSourceRepository dataSources;
    private final OntologyMappingRepository mappings;
    private final OntologyPhysicalObjectRepository objects;
    private final OntologyPhysicalFieldRepository fields;
    private final OntologyAiProposalRepository proposals;
    private final OntologyVersionRepository versions;
    private final OntologyTenantPersistence persistence;
    private final OntologyDraftService drafts;
    private final OntologyValidationService validation;
    private final OntologyCompilerService compiler;
    private final OntologyPublishService publisher;
    private final OntologyAiProposalService aiProposals;
    private final OntologyCatalogService catalog;
    private final OntologyReferencePackageService referencePackages;
    private final OntologyDataSourcePolicy dataSourcePolicy;
    private final OntologyDraftSafetyPolicy draftSafety;
    private final ObjectMapper objectMapper;

    public OntologyManagementService(
            OntologyWorkspaceRepository workspaces,
            OntologyDataSourceRepository dataSources,
            OntologyMappingRepository mappings,
            OntologyPhysicalObjectRepository objects,
            OntologyPhysicalFieldRepository fields,
            OntologyAiProposalRepository proposals,
            OntologyVersionRepository versions,
            OntologyTenantPersistence persistence,
            OntologyDraftService drafts,
            OntologyValidationService validation,
            OntologyCompilerService compiler,
            OntologyPublishService publisher,
            OntologyAiProposalService aiProposals,
            OntologyCatalogService catalog,
            OntologyReferencePackageService referencePackages,
            OntologyDataSourcePolicy dataSourcePolicy,
            OntologyDraftSafetyPolicy draftSafety,
            ObjectMapper objectMapper) {
        this.workspaces = workspaces;
        this.dataSources = dataSources;
        this.mappings = mappings;
        this.objects = objects;
        this.fields = fields;
        this.proposals = proposals;
        this.versions = versions;
        this.persistence = persistence;
        this.drafts = drafts;
        this.validation = validation;
        this.compiler = compiler;
        this.publisher = publisher;
        this.aiProposals = aiProposals;
        this.catalog = catalog;
        this.referencePackages = referencePackages;
        this.dataSourcePolicy = dataSourcePolicy;
        this.draftSafety = draftSafety;
        this.objectMapper = objectMapper;
    }

    public List<WorkspaceView> listWorkspaces() {
        String companyId = TenantContext.requireCompanyId();
        return workspaces.findByCompanyIdOrderByUpdatedAtDesc(companyId).stream()
                .map(this::workspaceView)
                .toList();
    }

    @Transactional
    public WorkspaceView createWorkspace(String userId, WorkspaceCreateRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String requestedKey = request == null ? null : request.key();
        String requestedName = request == null ? null : request.name();
        String requestedDescription = request == null ? null : request.description();
        draftSafety.validateWorkspaceMetadata(
                requestedKey, requestedName, requestedDescription);
        String key = requireKey(requestedKey);
        String name = requireName(requestedName);
        if (workspaces.findByCompanyIdAndKey(companyId, key).isPresent()) {
            throw new ConflictException("ONTOLOGY_KEY_CONFLICT");
        }
        OntologyWorkspaceEntity saved = persistNewWorkspaceOrKeyConflict(
                new OntologyWorkspaceEntity(companyId, key, name, requestedDescription, userId));
        return workspaceView(saved);
    }

    public WorkspaceView getWorkspace(Long workspaceId) {
        return workspaceView(requireWorkspace(workspaceId));
    }

    public WorkspaceView updateWorkspace(
            String userId,
            Long workspaceId,
            WorkspaceUpdateRequest request) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        if (request == null || request.expectedRevision() == null) {
            throw new IllegalArgumentException("ONTOLOGY_REVISION_REQUIRED");
        }
        if (request.key() != null && !Objects.equals(workspace.getKey(), request.key())) {
            throw new ConflictException("ONTOLOGY_KEY_IMMUTABLE");
        }
        OntologyDocument current = drafts.loadDraft(
                workspace.getCompanyId(), workspaceId, workspace);
        OntologyDocument updated = new OntologyDocument(
                current.key(),
                requireName(request.name()),
                request.description(),
                current.concepts(),
                current.relations(),
                current.metrics(),
                current.actions(),
                current.dataSources(),
                current.mappings());
        return workspaceView(drafts.saveDraft(
                workspace.getCompanyId(),
                userId,
                workspaceId,
                request.expectedRevision(),
                updated));
    }

    @Transactional
    public WorkspaceView archiveWorkspace(
            String userId,
            Long workspaceId,
            RevisionRequest request) {
        String companyId = TenantContext.requireCompanyId();
        OntologyWorkspaceEntity workspace = workspaces
                .findForUpdateByIdAndCompanyId(workspaceId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("ONTOLOGY_NOT_FOUND"));
        requireRevision(workspace, request == null ? null : request.expectedRevision());
        workspace.archive(userId);
        return workspaceView(persistence.saveForCurrentOrg(workspace));
    }

    public DraftView getDraft(Long workspaceId) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        OntologyDocument document = drafts.loadDraft(
                workspace.getCompanyId(), workspaceId, workspace);
        return draftView(workspace, document);
    }

    public DraftView saveDraft(
            String userId,
            Long workspaceId,
            DraftSaveRequest request) {
        if (request == null || request.expectedRevision() == null || request.document() == null) {
            throw new IllegalArgumentException("ONTOLOGY_DRAFT_REQUEST_INVALID");
        }
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        OntologyDocument current = drafts.loadDraft(
                workspace.getCompanyId(), workspaceId, workspace);
        OntologyDocument document = toDocument(request.document(), current, "MANUAL");
        draftSafety.validateDocument(document);
        OntologyWorkspaceEntity saved = drafts.saveDraft(
                workspace.getCompanyId(),
                userId,
                workspaceId,
                request.expectedRevision(),
                document);
        return draftView(saved, drafts.loadDraft(saved.getCompanyId(), workspaceId, saved));
    }

    public List<OntologyValidationService.ValidationIssue> validateDraft(Long workspaceId) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        return validation.validate(
                drafts.loadDraft(workspace.getCompanyId(), workspaceId, workspace), false);
    }

    public DraftDiffView diffDraft(Long workspaceId) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        OntologyDocument current = drafts.loadDraft(
                workspace.getCompanyId(), workspaceId, workspace);
        OntologyVersionEntity published = workspace.getPublishedVersion() == null
                ? null
                : versions.findByWorkspaceIdAndCompanyIdAndVersionNo(
                        workspaceId, workspace.getCompanyId(), workspace.getPublishedVersion())
                        .orElse(null);
        boolean changed = published == null
                || !Objects.equals(
                        compiler.compile(current, published.getVersionNo()).contentHash(),
                        published.getContentHash());
        return new DraftDiffView(
                workspace.getDraftRevision(),
                workspace.getPublishedVersion(),
                changed);
    }

    public ProposalView createProposal(
            String userId,
            Long workspaceId,
            ProposalRequest request) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        ProposalCommand command = new ProposalCommand(
                request == null ? null : request.instruction(),
                request == null ? List.of() : safe(request.selectedSources()),
                request == null ? null : request.mode());
        return aiProposals.propose(
                workspace.getCompanyId(), userId, workspaceId, command);
    }

    public List<ProposalRecordView> listProposals(Long workspaceId) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        return proposals.findByWorkspaceIdAndCompanyIdOrderByCreatedAtDesc(
                        workspaceId, workspace.getCompanyId()).stream()
                .map(this::proposalRecordView)
                .toList();
    }

    public ProposalRecordView getProposal(Long workspaceId, Long proposalId) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        return proposals.findByIdAndWorkspaceIdAndCompanyId(
                        proposalId, workspaceId, workspace.getCompanyId())
                .map(this::proposalRecordView)
                .orElseThrow(() -> new ResourceNotFoundException("AI_PROPOSAL_INVALID"));
    }

    public ProposalView applyProposal(
            String userId,
            Long workspaceId,
            Long proposalId,
            RevisionRequest request) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        proposals.findByIdAndWorkspaceIdAndCompanyId(
                        proposalId, workspaceId, workspace.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("AI_PROPOSAL_INVALID"));
        return aiProposals.apply(
                workspace.getCompanyId(),
                userId,
                proposalId,
                request == null ? null : request.expectedRevision());
    }

    public List<OntologyReferencePackageService.ReferencePackageSummary> listReferencePackages() {
        return referencePackages.list();
    }

    public ReferencePackageView getReferencePackage(String packageId) {
        OntologyReferencePackageService.ReferencePackage value =
                referencePackages.load(packageId);
        return new ReferencePackageView(
                value.id(),
                value.title(),
                value.description(),
                sanitizeDocument(value.document()),
                sourceSummaries(value.document().dataSources()));
    }

    @Transactional
    public WorkspaceView installReferencePackage(String userId, String packageId) {
        String companyId = TenantContext.requireCompanyId();
        OntologyReferencePackageService.ReferencePackage value =
                referencePackages.load(packageId);
        draftSafety.validateDocument(value.document());
        if (workspaces.findByCompanyIdAndKey(companyId, value.document().key()).isPresent()) {
            throw new ConflictException("ONTOLOGY_KEY_CONFLICT");
        }
        OntologyWorkspaceEntity workspace = persistNewWorkspaceOrKeyConflict(
                new OntologyWorkspaceEntity(
                        companyId,
                        value.document().key(),
                        value.document().name(),
                        value.document().description(),
                        userId,
                        OntologyWorkspaceEntity.CREATION_SOURCE_REFERENCE_PACKAGE,
                        value.id(),
                        value.fingerprint()));
        OntologyWorkspaceEntity installed = drafts.saveDraft(
                companyId, userId, workspace.getId(), 0L, value.document());
        return workspaceView(installed);
    }

    public List<SourceView> listDataSources(Long workspaceId) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        return dataSources.findByWorkspaceIdAndCompanyIdOrderByIdAsc(
                        workspaceId, workspace.getCompanyId()).stream()
                .map(this::sourceView)
                .toList();
    }

    public DraftView createDataSource(
            String userId,
            Long workspaceId,
            DataSourceMutationRequest request) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        OntologyDocument current = drafts.loadDraft(
                workspace.getCompanyId(), workspaceId, workspace);
        DataSourceInput input = normalizedSourceInput(requireSourceInput(request));
        if (input.id() != null) {
            throw new IllegalArgumentException("ONTOLOGY_DATA_SOURCE_ID_SERVER_ASSIGNED");
        }
        if (current.dataSources().stream().anyMatch(source -> Objects.equals(
                source.key(), input.key()))) {
            throw new ConflictException("ONTOLOGY_DATA_SOURCE_KEY_CONFLICT");
        }
        List<OntologyDocument.DataSource> sources = new ArrayList<>(current.dataSources());
        sources.add(toDataSource(input));
        return saveServerDocument(
                userId,
                workspace,
                request.expectedRevision(),
                replaceSources(current, sources));
    }

    public DraftView updateDataSource(
            String userId,
            Long workspaceId,
            Long dataSourceId,
            DataSourceMutationRequest request) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        OntologyDocument current = drafts.loadDraft(
                workspace.getCompanyId(), workspaceId, workspace);
        DataSourceInput input = normalizedSourceInput(requireSourceInput(request));
        OntologyDocument.DataSource existing = current.dataSources().stream()
                .filter(source -> Objects.equals(source.id(), dataSourceId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ONTOLOGY_DATA_SOURCE_NOT_FOUND"));
        if (input.id() != null && !Objects.equals(input.id(), dataSourceId)) {
            throw new ConflictException("ONTOLOGY_DATA_SOURCE_ID_MISMATCH");
        }
        if (!Objects.equals(existing.key(), input.key())) {
            throw new ConflictException("ONTOLOGY_DATA_SOURCE_KEY_IMMUTABLE");
        }
        List<OntologyDocument.DataSource> sources = current.dataSources().stream()
                .map(source -> Objects.equals(source.id(), dataSourceId)
                        ? new OntologyDocument.DataSource(
                                source.id(),
                                source.key(),
                                input.name(),
                                input.type(),
                                input.configJson(),
                                input.sampleDataJson())
                        : source)
                .toList();
        return saveServerDocument(
                userId,
                workspace,
                request.expectedRevision(),
                replaceSources(current, sources));
    }

    public CatalogObjectMutationView discoverObjects(
                    String userId,
                    Long workspaceId,
                    Long dataSourceId,
                    RevisionRequest request) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        CatalogMutation<com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalObject>
                discovered = catalog.discoverObjects(
                workspace.getCompanyId(),
                userId,
                workspaceId,
                dataSourceId,
                request == null ? null : request.expectedRevision());
        return new CatalogObjectMutationView(
                discovered.items().stream()
                        .map(value -> new DiscoveredObjectView(
                                value.key(), value.name(), value.objectType()))
                        .toList(),
                discovered.revision());
    }

    public CatalogFieldMutationView discoverFields(
                    String userId,
                    Long workspaceId,
                    Long dataSourceId,
                    DiscoverFieldsRequest request) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        CatalogMutation<com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalField>
                discovered = catalog.discoverFields(
                workspace.getCompanyId(),
                userId,
                workspaceId,
                dataSourceId,
                request == null ? null : request.objectKey(),
                request == null ? null : request.expectedRevision());
        return new CatalogFieldMutationView(
                discovered.items().stream()
                        .map(value -> new DiscoveredFieldView(
                                value.objectKey(),
                                value.key(),
                                value.name(),
                                value.dataType(),
                                value.nullable(),
                                value.multiple()))
                        .toList(),
                discovered.revision());
    }

    public CatalogView getCatalog(Long workspaceId) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        List<PhysicalObjectView> objectViews = new ArrayList<>();
        for (OntologyPhysicalObjectEntity object : objects
                .findByWorkspaceIdAndCompanyIdOrderByIdAsc(workspaceId, workspace.getCompanyId())) {
            List<PhysicalFieldView> fieldViews = fields
                    .findByPhysicalObjectIdAndWorkspaceIdAndCompanyIdOrderByIdAsc(
                            object.getId(), workspaceId, workspace.getCompanyId()).stream()
                    .map(this::physicalFieldView)
                    .toList();
            objectViews.add(new PhysicalObjectView(
                    object.getId(),
                    object.getDataSourceId(),
                    object.getObjectKey(),
                    object.getName(),
                    object.getObjectType(),
                    object.getDiscoveredAt(),
                    fieldViews));
        }
        return new CatalogView(workspace.getDraftRevision(), List.copyOf(objectViews));
    }

    public List<MappingView> listMappings(Long workspaceId) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        return mappings.findByWorkspaceIdAndCompanyIdOrderByIdAsc(
                        workspaceId, workspace.getCompanyId()).stream()
                .map(this::mappingView)
                .toList();
    }

    public DraftView replaceMappings(
            String userId,
            Long workspaceId,
            MappingReplaceRequest request) {
        if (request == null || request.expectedRevision() == null) {
            throw new IllegalArgumentException("ONTOLOGY_REVISION_REQUIRED");
        }
        if (request.mappings() == null) {
            throw new IllegalArgumentException("ONTOLOGY_VALIDATION_FAILED");
        }
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        OntologyDocument current = drafts.loadDraft(
                workspace.getCompanyId(), workspaceId, workspace);
        List<OntologyDocument.Mapping> replacements = request.mappings().stream()
                .map(input -> toMapping(input, "MANUAL"))
                .toList();
        draftSafety.validateMappings(replacements);
        OntologyDocument updated = new OntologyDocument(
                current.key(), current.name(), current.description(), current.concepts(),
                current.relations(), current.metrics(), current.actions(),
                current.dataSources(), replacements);
        return saveServerDocument(
                userId, workspace, request.expectedRevision(), updated);
    }

    public MappingValidationBatchView validateMappings(
            String userId,
            Long workspaceId,
            MappingValidationRequest request) {
        if (request == null || request.expectedRevision() == null || safe(request.mappings()).isEmpty()) {
            throw new IllegalArgumentException("MAPPING_IDENTITY_REQUIRED");
        }
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        List<MappingIdentityInput> inputs = safe(request.mappings());
        List<MappingKey> keys = inputs.stream()
                .map(input -> {
                    if (input == null) {
                        throw new IllegalArgumentException("MAPPING_IDENTITY_REQUIRED");
                    }
                    return new MappingKey(
                            input.targetType(), input.targetKey(), input.dataSourceId());
                })
                .toList();
        MappingBatchCommit committed = catalog.validateMappings(
                workspace.getCompanyId(),
                userId,
                workspaceId,
                request.expectedRevision(),
                keys);
        List<MappingValidationResultView> results = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            MappingIdentityInput input = inputs.get(index);
            MappingValidation validationResult = committed.validations().get(index);
            results.add(new MappingValidationResultView(
                    input,
                    validationResult.valid(),
                    validationResult.code(),
                    validationResult.message()));
        }
        return new MappingValidationBatchView(committed.revision(), List.copyOf(results));
    }

    @Transactional
    public CompilePreviewView compilePreview(
            Long workspaceId,
            RevisionRequest request) {
        String companyId = TenantContext.requireCompanyId();
        OntologyWorkspaceEntity workspace = workspaces
                .findForUpdateByIdAndCompanyId(workspaceId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("ONTOLOGY_NOT_FOUND"));
        requireRevision(workspace, request == null ? null : request.expectedRevision());
        OntologyDocument document = drafts.loadDraft(
                workspace.getCompanyId(), workspaceId, workspace);
        int nextVersion = workspace.getPublishedVersion() == null
                ? 1 : workspace.getPublishedVersion() + 1;
        OntologyCompilerService.CompiledContracts compiled = compiler.compile(
                document, nextVersion);
        return new CompilePreviewView(
                nextVersion,
                workspace.getDraftRevision(),
                compiled.contentHash(),
                compiled.jsonSchema(),
                compiled.graphqlSdl(),
                compiled.queryContractJson());
    }

    public VersionSummaryView publish(
            String userId,
            Long workspaceId,
            RevisionRequest request) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        return versionSummary(publisher.publish(
                workspace.getCompanyId(),
                userId,
                workspaceId,
                request == null ? null : request.expectedRevision()));
    }

    public List<VersionSummaryView> listVersions(Long workspaceId) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        return versions.findByWorkspaceIdAndCompanyIdOrderByVersionNoDesc(
                        workspaceId, workspace.getCompanyId()).stream()
                .map(this::versionSummary)
                .toList();
    }

    public VersionDetailView getVersion(Long workspaceId, Integer versionNo) {
        OntologyWorkspaceEntity workspace = requireWorkspace(workspaceId);
        OntologyVersionEntity version = versions
                .findByWorkspaceIdAndCompanyIdAndVersionNo(
                        workspaceId, workspace.getCompanyId(), versionNo)
                .orElseThrow(() -> new ResourceNotFoundException("ONTOLOGY_VERSION_NOT_FOUND"));
        OntologyDocument snapshot = readJson(
                version.getSnapshotJson(), OntologyDocument.class, "ONTOLOGY_VERSION_INVALID");
        return new VersionDetailView(
                versionSummary(version),
                sanitizeDocument(snapshot),
                version.getJsonSchema(),
                version.getGraphqlSdl(),
                version.getQueryContractJson(),
                readJsonList(version.getValidationSummaryJson()));
    }

    private DraftView saveServerDocument(
            String userId,
            OntologyWorkspaceEntity workspace,
            Long expectedRevision,
            OntologyDocument document) {
        draftSafety.validateDocument(document);
        OntologyWorkspaceEntity saved = drafts.saveDraft(
                workspace.getCompanyId(),
                userId,
                workspace.getId(),
                expectedRevision,
                document);
        return draftView(
                saved,
                drafts.loadDraft(saved.getCompanyId(), saved.getId(), saved));
    }

    private DraftView draftView(OntologyWorkspaceEntity workspace, OntologyDocument document) {
        return new DraftView(
                workspaceView(workspace),
                workspace.getId(),
                workspace.getKey(),
                workspace.getStatus(),
                workspace.getDraftRevision(),
                workspace.getPublishedVersion(),
                sanitizeDocument(document),
                dataSources.findByWorkspaceIdAndCompanyIdOrderByIdAsc(
                                workspace.getId(), workspace.getCompanyId()).stream()
                        .map(this::sourceView)
                        .toList());
    }

    private OntologyDocument sanitizeDocument(OntologyDocument document) {
        return new OntologyDocument(
                document.key(),
                document.name(),
                document.description(),
                document.concepts(),
                document.relations(),
                document.metrics(),
                document.actions(),
                safe(document.dataSources()).stream()
                        .map(source -> new OntologyDocument.DataSource(
                                source.id(), source.key(), source.name(), source.type(), null, null))
                        .toList(),
                document.mappings());
    }

    private List<SourceView> sourceSummaries(List<OntologyDocument.DataSource> values) {
        return safe(values).stream()
                .map(source -> new SourceView(
                        source.id(),
                        source.key(),
                        source.name(),
                        source.type().name(),
                        dataSourcePolicy.adapterKey(source.configJson()),
                        "REFERENCE",
                        null,
                        dataSourcePolicy.sampleSummary(source.sampleDataJson())))
                .toList();
    }

    private SourceView sourceView(OntologyDataSourceEntity source) {
        return new SourceView(
                source.getId(),
                source.getKey(),
                source.getName(),
                source.getSourceType(),
                dataSourcePolicy.adapterKey(source.getConfigJson()),
                source.getStatus(),
                source.getLastValidatedAt(),
                dataSourcePolicy.sampleSummary(source.getSampleDataJson()));
    }

    private WorkspaceView workspaceView(OntologyWorkspaceEntity workspace) {
        return new WorkspaceView(
                workspace.getId(),
                workspace.getKey(),
                workspace.getName(),
                workspace.getDescription(),
                workspace.getCreatedBy(),
                workspace.getCreationSource(),
                workspace.getReferencePackageId(),
                workspace.getReferencePackageFingerprint(),
                workspace.getStatus(),
                workspace.getDraftRevision(),
                workspace.getPublishedVersion(),
                workspace.getCreatedAt(),
                workspace.getUpdatedAt());
    }

    private OntologyWorkspaceEntity persistNewWorkspaceOrKeyConflict(
            OntologyWorkspaceEntity workspace) {
        try {
            OntologyWorkspaceEntity saved = persistence.saveForCurrentOrg(workspace);
            persistence.flushForCurrentOrg(workspace.getCompanyId());
            return saved;
        } catch (DataIntegrityViolationException exception) {
            if (isWorkspaceKeyUniqueViolation(exception)) {
                throw new ConflictException("ONTOLOGY_KEY_CONFLICT");
            }
            throw exception;
        }
    }

    private boolean isWorkspaceKeyUniqueViolation(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && WORKSPACE_KEY_UNIQUE_CONSTRAINT.equals(
                            constraintViolation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private ProposalRecordView proposalRecordView(OntologyAiProposalEntity entity) {
        boolean hasCandidate = "READY".equals(entity.getStatus())
                || "APPLIED".equals(entity.getStatus());
        OntologyDocument candidate = hasCandidate
                ? readJson(entity.getPayloadJson(), OntologyDocument.class, "AI_PROPOSAL_INVALID")
                : null;
        ProposalDiff diff = entity.getDiffJson() == null
                ? null
                : readJson(entity.getDiffJson(), ProposalDiff.class, "AI_PROPOSAL_INVALID");
        List<OntologyValidationService.ValidationIssue> issues = proposalIssues(entity);
        return new ProposalRecordView(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getProposalType(),
                entity.getStatus(),
                candidate == null ? null : sanitizeDocument(candidate),
                diff,
                issues,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getAppliedAt());
    }

    private List<OntologyValidationService.ValidationIssue> proposalIssues(
            OntologyAiProposalEntity entity) {
        if (entity.getValidationJson() == null) {
            return List.of();
        }
        if (!"FAILED".equals(entity.getStatus())) {
            return readJson(
                    entity.getValidationJson(),
                    new TypeReference<>() { },
                    "AI_PROPOSAL_INVALID");
        }
        ProposalFailure failure = readJson(
                entity.getValidationJson(), ProposalFailure.class, "AI_PROPOSAL_INVALID");
        if (failure.code() == null
                || !failure.code().matches("[A-Z][A-Z0-9_]{2,63}")
                || failure.diagnostic() == null
                || !failure.diagnostic().matches("[A-Z][A-Z0-9_]{2,127}")) {
            throw new IllegalStateException("AI_PROPOSAL_INVALID");
        }
        return List.of(new OntologyValidationService.ValidationIssue(
                failure.code(),
                OntologyValidationService.Severity.ERROR,
                "$",
                failure.diagnostic()));
    }

    private PhysicalFieldView physicalFieldView(OntologyPhysicalFieldEntity field) {
        return new PhysicalFieldView(
                field.getId(),
                field.getFieldKey(),
                field.getName(),
                field.getDataType(),
                field.isNullable(),
                field.isMultiple(),
                field.getDiscoveredAt());
    }

    private MappingView mappingView(OntologyMappingEntity mapping) {
        return new MappingView(
                mapping.getId(),
                mapping.getTargetType(),
                mapping.getTargetKey(),
                mapping.getDataSourceId(),
                mapping.getPhysicalObjectKey(),
                mapping.getPhysicalFieldKey(),
                mapping.getRelationTargetFieldKey(),
                mapping.getTransform(),
                mapping.getConfidence().doubleValue(),
                mapping.getSource(),
                mapping.getValidationStatus(),
                mapping.getLastValidatedAt());
    }

    private VersionSummaryView versionSummary(OntologyVersionEntity version) {
        return new VersionSummaryView(
                version.getVersionNo(),
                version.getSourceDraftRevision(),
                version.getContentHash(),
                version.getPublishedBy(),
                version.getPublishedAt());
    }

    private OntologyDocument toDocument(
            DraftDocumentInput input,
            OntologyDocument current,
            String origin) {
        if (input.concepts() == null
                || input.relations() == null
                || input.metrics() == null
                || input.actions() == null
                || input.dataSources() == null
                || input.mappings() == null) {
            throw new IllegalArgumentException("ONTOLOGY_VALIDATION_FAILED");
        }
        Map<Long, OntologyDocument.DataSource> serverSources = new LinkedHashMap<>();
        safe(current.dataSources()).forEach(source -> serverSources.put(source.id(), source));
        List<OntologyDocument.DataSource> hydratedSources = input.dataSources().stream()
                .map(inputSource -> {
                    if (inputSource == null || inputSource.id() == null) {
                        throw new IllegalArgumentException(
                                "DATA_SOURCE_WRITE_REQUIRES_DEDICATED_ENDPOINT");
                    }
                    OntologyDocument.DataSource server = serverSources.get(inputSource.id());
                    if (server == null || !Objects.equals(server.key(), inputSource.key())) {
                        throw new ResourceNotFoundException("ONTOLOGY_DATA_SOURCE_NOT_FOUND");
                    }
                    return server;
                })
                .toList();
        return new OntologyDocument(
                input.key(),
                input.name(),
                input.description(),
                input.concepts(),
                input.relations(),
                input.metrics(),
                input.actions(),
                hydratedSources,
                input.mappings().stream().map(value -> toMapping(value, origin)).toList());
    }

    private OntologyDocument.DataSource toDataSource(DataSourceInput input) {
        if (input == null || input.type() == null) {
            throw new IllegalArgumentException("ONTOLOGY_DATA_SOURCE_INVALID");
        }
        return new OntologyDocument.DataSource(
                input.id(), input.key(), input.name(), input.type(),
                input.configJson(), input.sampleDataJson());
    }

    private OntologyDocument.Mapping toMapping(MappingInput input, String origin) {
        if (input == null) {
            throw new IllegalArgumentException("ONTOLOGY_VALIDATION_FAILED");
        }
        return new OntologyDocument.Mapping(
                input.targetType(),
                input.targetKey(),
                input.dataSourceId(),
                input.physicalObjectKey(),
                input.physicalFieldKey(),
                input.relationTargetFieldKey(),
                input.transform(),
                input.confidence(),
                origin,
                "PENDING");
    }

    private OntologyDocument replaceSources(
            OntologyDocument current,
            List<OntologyDocument.DataSource> sources) {
        return new OntologyDocument(
                current.key(), current.name(), current.description(), current.concepts(),
                current.relations(), current.metrics(), current.actions(), sources, current.mappings());
    }

    private DataSourceInput requireSourceInput(DataSourceMutationRequest request) {
        if (request == null || request.expectedRevision() == null || request.source() == null) {
            throw new IllegalArgumentException("ONTOLOGY_DATA_SOURCE_INVALID");
        }
        return request.source();
    }

    private DataSourceInput normalizedSourceInput(DataSourceInput input) {
        if (input.type() == null) {
            throw new IllegalArgumentException("ONTOLOGY_DATA_SOURCE_INVALID");
        }
        return new DataSourceInput(
                input.id(),
                requireKey(input.key()),
                requireName(input.name()),
                input.type(),
                input.configJson(),
                input.sampleDataJson());
    }

    private OntologyWorkspaceEntity requireWorkspace(Long workspaceId) {
        String companyId = TenantContext.requireCompanyId();
        return workspaces.findByIdAndCompanyId(workspaceId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("ONTOLOGY_NOT_FOUND"));
    }

    private void requireRevision(OntologyWorkspaceEntity workspace, Long expectedRevision) {
        if (expectedRevision == null
                || !Objects.equals(workspace.getDraftRevision(), expectedRevision)) {
            throw new ConflictException("ONTOLOGY_REVISION_CONFLICT");
        }
        if ("ARCHIVED".equals(workspace.getStatus())) {
            throw new ConflictException("ONTOLOGY_WORKSPACE_ARCHIVED");
        }
    }

    private String requireKey(String value) {
        if (value == null || value.length() > 128 || !KEY_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("ONTOLOGY_KEY_INVALID");
        }
        return value;
    }

    private String requireName(String value) {
        if (value == null || value.isBlank() || value.length() > 160) {
            throw new IllegalArgumentException("ONTOLOGY_NAME_INVALID");
        }
        return value.trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("ONTOLOGY_SERIALIZATION_FAILED", exception);
        }
    }

    private <T> T readJson(String json, Class<T> type, String code) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(code, exception);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type, String code) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(code, exception);
        }
    }

    private List<Object> readJsonList(String json) {
        return readJson(json, new TypeReference<>() { }, "ONTOLOGY_VERSION_INVALID");
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record WorkspaceCreateRequest(String key, String name, String description) {
    }

    public record WorkspaceUpdateRequest(
            String key,
            String name,
            String description,
            Long expectedRevision) {
    }

    public record RevisionRequest(Long expectedRevision) {
    }

    public record DraftSaveRequest(Long expectedRevision, DraftDocumentInput document) {
    }

    public record DraftDocumentInput(
            String key,
            String name,
            String description,
            List<OntologyDocument.Concept> concepts,
            List<OntologyDocument.Relation> relations,
            List<OntologyDocument.Metric> metrics,
            List<OntologyDocument.Action> actions,
            List<DraftDataSourceInput> dataSources,
            List<MappingInput> mappings) {
    }

    public record DraftDataSourceInput(
            Long id,
            String key,
            String name,
            OntologyDocument.SourceType type) {
    }

    public record DataSourceInput(
            Long id,
            String key,
            String name,
            OntologyDocument.SourceType type,
            String configJson,
            String sampleDataJson) {
    }

    public record MappingInput(
            String targetType,
            String targetKey,
            Long dataSourceId,
            String physicalObjectKey,
            String physicalFieldKey,
            String relationTargetFieldKey,
            String transform,
            double confidence) {
    }

    public record ProposalRequest(
            String instruction,
            List<OntologyAiProposalService.SourceSelection> selectedSources,
            String mode) {
    }

    public record DataSourceMutationRequest(Long expectedRevision, DataSourceInput source) {
    }

    public record DiscoverFieldsRequest(String objectKey, Long expectedRevision) {
    }

    public record MappingReplaceRequest(Long expectedRevision, List<MappingInput> mappings) {
    }

    public record MappingIdentityInput(String targetType, String targetKey, Long dataSourceId) {
    }

    public record MappingValidationRequest(
            Long expectedRevision,
            List<MappingIdentityInput> mappings) {
    }

    public record WorkspaceView(
            Long id,
            String key,
            String name,
            String description,
            String createdBy,
            String creationSource,
            String referencePackageId,
            String referencePackageFingerprint,
            String status,
            Long draftRevision,
            Integer publishedVersion,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record DraftView(
            WorkspaceView workspace,
            Long id,
            String key,
            String status,
            Long draftRevision,
            Integer publishedVersion,
            OntologyDocument document,
            List<SourceView> sources) {
    }

    public record SourceView(
            Long id,
            String key,
            String name,
            String type,
            String adapterKey,
            String status,
            Instant lastValidatedAt,
            OntologyDataSourcePolicy.SampleSummary sample) {
    }

    public record DraftDiffView(Long draftRevision, Integer publishedVersion, boolean changed) {
    }

    public record ProposalRecordView(
            Long id,
            Long workspaceId,
            String proposalType,
            String status,
            OntologyDocument candidate,
            ProposalDiff diff,
            List<OntologyValidationService.ValidationIssue> validation,
            Instant createdAt,
            Instant updatedAt,
            Instant appliedAt) {
    }

    private record ProposalFailure(String code, String diagnostic) {
    }

    public record ReferencePackageView(
            String id,
            String title,
            String description,
            OntologyDocument document,
            List<SourceView> sources) {
    }

    public record CatalogView(Long revision, List<PhysicalObjectView> objects) {
    }

    public record CatalogObjectMutationView(
            List<DiscoveredObjectView> items,
            long revision) {
    }

    public record CatalogFieldMutationView(
            List<DiscoveredFieldView> items,
            long revision) {
    }

    public record DiscoveredObjectView(String key, String name, String type) {
    }

    public record DiscoveredFieldView(
            String objectKey,
            String key,
            String name,
            String dataType,
            boolean nullable,
            boolean multiple) {
    }

    public record PhysicalObjectView(
            Long id,
            Long dataSourceId,
            String key,
            String name,
            String type,
            Instant discoveredAt,
            List<PhysicalFieldView> fields) {
    }

    public record PhysicalFieldView(
            Long id,
            String key,
            String name,
            String dataType,
            boolean nullable,
            boolean multiple,
            Instant discoveredAt) {
    }

    public record MappingView(
            Long id,
            String targetType,
            String targetKey,
            Long dataSourceId,
            String physicalObjectKey,
            String physicalFieldKey,
            String relationTargetFieldKey,
            String transform,
            double confidence,
            String source,
            String validationStatus,
            Instant lastValidatedAt) {
    }

    public record MappingValidationResultView(
            MappingIdentityInput mapping,
            boolean valid,
            String code,
            String message) {
    }

    public record MappingValidationBatchView(
            long revision,
            List<MappingValidationResultView> results) {
    }

    public record CompilePreviewView(
            int version,
            Long sourceDraftRevision,
            String contentHash,
            String jsonSchema,
            String graphqlSdl,
            String queryContractJson) {
    }

    public record VersionSummaryView(
            Integer version,
            Long sourceDraftRevision,
            String contentHash,
            String publishedBy,
            Instant publishedAt) {
    }

    public record VersionDetailView(
            VersionSummaryView summary,
            OntologyDocument document,
            String jsonSchema,
            String graphqlSdl,
            String queryContractJson,
            List<Object> validation) {
    }
}
