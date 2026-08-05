package com.codehouse.ciciassistant.ontology.semattice;

import com.codehouse.ciciassistant.auth.domain.SematticeProvisioningBindingEntity;
import com.codehouse.ciciassistant.auth.domain.SematticeProvisioningBindingRepository;
import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.ontology.service.OntologyDraftService;
import com.codehouse.ciciassistant.ontology.service.OntologyPublishService;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyContractCompiler.Contract;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyContractCompiler.FieldDefinition;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyContractCompiler.ObjectDefinition;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyContractCompiler.RelationDefinition;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyStateStore.Binding;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyStateStore.ElementBinding;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyStateStore.Operation;
import com.codehouse.ciciassistant.semattice.SematticeMetadataApprovalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SematticeOntologyLifecycleService {

    private static final String GET_CURRENT = "metadata.version.get-current";
    private static final String VERSION_CREATE = "metadata.version.create";
    private static final String OBJECT_UPSERT = "metadata.object.upsert";
    private static final String FIELD_UPSERT = "metadata.field.upsert";
    private static final String RELATION_UPSERT = "metadata.relation.upsert";
    private static final String VERSION_PUBLISH = "metadata.version.publish";
    private static final String CHANGESET_VALIDATE = "metadata.changeset.validate";
    private static final String CHANGESET_SIMULATE = "metadata.changeset.simulate";
    private static final String CHANGESET_APPROVE = "metadata.changeset.approve";
    private static final String CHANGESET_STATUS = "metadata.changeset.get-status";
    private static final String CHANGESET_BACKFILL = "metadata.changeset.backfill";
    private static final String CHANGESET_COVERAGE = "metadata.changeset.validate-coverage";
    private static final String CHANGESET_PUBLISH = "metadata.changeset.publish";
    private static final String CHANGESET_CANCEL = "metadata.changeset.cancel";
    private static final String CHANGESET_ROLLBACK = "metadata.changeset.rollback";
    private static final int BACKFILL_BATCH_SIZE = 250;

    private final OntologyWorkspaceRepository workspaces;
    private final OntologyDraftService drafts;
    private final OntologyPublishService publisher;
    private final SematticeProvisioningBindingRepository provisioningBindings;
    private final SematticeOntologyGateway gateway;
    private final SematticeOntologyContractCompiler compiler;
    private final SematticeOntologyStateStore states;
    private final SematticeMetadataApprovalService approvals;
    private final ObjectMapper objectMapper;

    public SematticeOntologyLifecycleService(
            OntologyWorkspaceRepository workspaces,
            OntologyDraftService drafts,
            OntologyPublishService publisher,
            SematticeProvisioningBindingRepository provisioningBindings,
            SematticeOntologyGateway gateway,
            SematticeOntologyContractCompiler compiler,
            SematticeOntologyStateStore states,
            SematticeMetadataApprovalService approvals,
            ObjectMapper objectMapper) {
        this.workspaces = workspaces;
        this.drafts = drafts;
        this.publisher = publisher;
        this.provisioningBindings = provisioningBindings;
        this.gateway = gateway;
        this.compiler = compiler;
        this.states = states;
        this.approvals = approvals;
        this.objectMapper = objectMapper;
    }

    public BindingView link(String companyId, String userId, Long workspaceId) {
        OntologyWorkspaceEntity workspace = requireWorkspace(companyId, workspaceId);
        String tenantId = requireProvisionedTenant(companyId);
        JsonNode remote = currentMetadata(companyId, userId, false);
        RemoteBundle bundle = remote == null ? null : parseRemote(remote);
        Instant now = Instant.now();
        Binding saved = states.saveBinding(new Binding(
                companyId,
                workspaceId,
                tenantId,
                bundle == null ? null : bundle.versionId(),
                bundle == null ? null : bundle.sequence(),
                bundle == null ? null : bundle.digest(),
                "LINKED",
                null,
                now,
                now,
                now));
        if (bundle != null) {
            bindMatchingElements(workspace, bundle);
        }
        return bindingView(saved);
    }

    public BindingView status(String companyId, Long workspaceId) {
        requireWorkspace(companyId, workspaceId);
        return states.findBinding(companyId, workspaceId)
                .map(this::bindingView)
                .orElseGet(() -> new BindingView(
                        workspaceId, "NOT_LINKED", null, null, null,
                        null, null, 0));
    }

    public BindingView checkDrift(String companyId, String userId, Long workspaceId) {
        requireWorkspace(companyId, workspaceId);
        Binding current = requireBinding(companyId, workspaceId);
        RemoteBundle remote = parseRemote(currentMetadata(companyId, userId, true));
        boolean sameVersion = Objects.equals(
                current.activeMetadataVersionId(), remote.versionId());
        boolean sameDigest = blank(current.activeDigest())
                || blank(remote.digest())
                || Objects.equals(current.activeDigest(), remote.digest());
        Instant now = Instant.now();
        Binding updated = states.saveBinding(new Binding(
                current.companyId(), current.workspaceId(), current.sematticeTenantId(),
                remote.versionId(), remote.sequence(), remote.digest(),
                sameVersion && sameDigest ? "IN_SYNC" : "DRIFTED",
                sameVersion && sameDigest ? null : "SEMATTICE_METADATA_DRIFTED",
                now, current.createdAt(), now));
        return bindingView(updated);
    }

    public ImportProposal importProposal(String companyId, String userId, Long workspaceId) {
        OntologyWorkspaceEntity workspace = requireWorkspace(companyId, workspaceId);
        requireBinding(companyId, workspaceId);
        JsonNode remote = currentMetadata(companyId, userId, true);
        RemoteBundle bundle = parseRemote(remote);
        Map<String, List<JsonNode>> fieldsByObject = new LinkedHashMap<>();
        for (JsonNode field : remote.path("fields")) {
            if (!"tombstone".equalsIgnoreCase(field.path("lifecycle_state").asText())) {
                fieldsByObject.computeIfAbsent(field.path("object_id").asText(), ignored -> new ArrayList<>())
                        .add(field);
            }
        }
        List<OntologyDocument.Concept> concepts = new ArrayList<>();
        Map<String, String> conceptKeyByObjectId = new LinkedHashMap<>();
        for (JsonNode object : remote.path("objects")) {
            String key = object.path("api_name").asText();
            conceptKeyByObjectId.put(object.path("object_id").asText(), key);
            List<OntologyDocument.Property> properties = fieldsByObject
                    .getOrDefault(object.path("object_id").asText(), List.of()).stream()
                    .map(this::toProperty)
                    .toList();
            concepts.add(new OntologyDocument.Concept(
                    key,
                    nonBlank(object.path("label").asText(), key),
                    nonBlank(object.path("label").asText(), key),
                    object.path("description").asText(),
                    conceptType(object.path("semantic")),
                    properties.isEmpty() ? null : properties.getFirst().key(),
                    120 + (concepts.size() % 4) * 260,
                    120 + (concepts.size() / 4) * 180,
                    true,
                    true,
                    properties));
        }
        List<OntologyDocument.Relation> relations = new ArrayList<>();
        for (JsonNode relation : remote.path("relations")) {
            String sourceKey = conceptKeyByObjectId.get(relation.path("source_object_id").asText());
            String targetKey = conceptKeyByObjectId.get(relation.path("target_object_id").asText());
            if (sourceKey == null || targetKey == null) {
                continue;
            }
            String key = relation.path("api_name").asText();
            relations.add(new OntologyDocument.Relation(
                    key,
                    nonBlank(relation.path("semantic").path("forward_label").asText(), key),
                    relation.path("description").asText(),
                    sourceKey,
                    targetKey,
                    cardinality(relation),
                    relation.path("semantic").path("forward_label").asText(),
                    relation.path("semantic").path("reverse_label").asText(),
                    true,
                    true));
        }
        OntologyDocument current = drafts.loadDraft(companyId, workspaceId, workspace);
        OntologyDocument candidate = new OntologyDocument(
                current.key(), current.name(), current.description(),
                List.copyOf(concepts), List.copyOf(relations),
                current.metrics(), current.actions(), current.dataSources(), current.mappings());
        return new ImportProposal(
                workspace.getDraftRevision(), bundle.versionId(), bundle.sequence(),
                bundle.digest(), sanitize(candidate),
                new ImportDiff(concepts.size(), relations.size(),
                        fieldsByObject.values().stream().mapToInt(List::size).sum()));
    }

    public OperationView prepare(
            String companyId,
            String userId,
            Long workspaceId,
            long expectedRevision) {
        OntologyWorkspaceEntity workspace = requireWorkspace(companyId, workspaceId);
        if (!Objects.equals(workspace.getDraftRevision(), expectedRevision)) {
            throw new ConflictException("ONTOLOGY_REVISION_CONFLICT");
        }
        Binding binding = requireBinding(companyId, workspaceId);
        OntologyDocument document = drafts.loadDraft(companyId, workspaceId, workspace);
        Contract contract = compiler.compile(workspaceId, expectedRevision, document);
        JsonNode remoteNode = currentMetadata(companyId, userId, false);
        RemoteBundle remote = remoteNode == null ? null : parseRemote(remoteNode);
        if (remote != null
                && !blank(binding.activeMetadataVersionId())
                && !Objects.equals(binding.activeMetadataVersionId(), remote.versionId())) {
            markBindingFailed(binding, "DRIFTED", "SEMATTICE_METADATA_DRIFTED");
            throw new ConflictException("SEMATTICE_METADATA_DRIFTED");
        }
        String operationType = remote == null ? "INITIAL_PUBLISH" : "CHANGESET";
        Operation existing = states.findOperationByRevision(
                        companyId, workspaceId, operationType, expectedRevision, contract.sourceDigest())
                .orElse(null);
        if (existing != null && !"FAILED".equals(existing.status())) {
            return operationViewWithImpact(companyId, userId, existing);
        }
        Instant now = Instant.now();
        Operation operation = existing == null
                ? states.saveOperation(new Operation(
                        UUID.randomUUID().toString(), companyId, workspaceId, operationType,
                        expectedRevision, contract.sourceDigest(),
                        remote == null ? null : remote.versionId(), null, null, null, null, null,
                        "COMPILING", null, false, userId, null, null, now, now, null))
                : states.saveOperation(withState(existing, "COMPILING", null));
        markBindingFailed(binding, "PUBLISHING", null);
        try {
            operation = compileCandidate(companyId, userId, workspace, contract, remote, operation);
            return operationViewWithImpact(companyId, userId, operation);
        } catch (RuntimeException exception) {
            states.saveOperation(withState(operation, "FAILED", safeError(exception)));
            markBindingFailed(binding, "FAILED", safeError(exception));
            throw exception;
        }
    }

    public OperationView requestApproval(
            String companyId,
            String userId,
            Long workspaceId,
            String operationId) {
        Operation operation = requireOperation(companyId, workspaceId, operationId);
        requireRequester(operation, userId);
        if (blank(operation.subjectType()) || blank(operation.subjectId())) {
            throw new ConflictException("SEMATTICE_APPROVAL_SUBJECT_NOT_READY");
        }
        if (!blank(operation.approvalRequestId())) {
            return operationViewWithImpact(companyId, userId, operation);
        }
        SematticeMetadataApprovalService.ApprovalView approval = approvals.request(
                companyId,
                userId,
                operation.subjectType(),
                operation.subjectId(),
                ("ROLLBACK".equals(operation.operationType()) ? "回滚" : "发布")
                        + "业务本体工作区 " + workspaceId
                        + "，来源修订 " + operation.sourceRevision());
        Operation updated = states.saveOperation(new Operation(
                operation.operationId(), operation.companyId(), operation.workspaceId(),
                operation.operationType(), operation.sourceRevision(), operation.sourceDigest(),
                operation.baseMetadataVersionId(), operation.candidateMetadataVersionId(),
                operation.changesetId(), operation.subjectType(), operation.subjectId(),
                approval.approvalId(), "APPROVAL_PENDING", operation.riskLevel(),
                operation.requiresBackfill(), operation.requestedBy(), operation.approvedBy(),
                null, operation.createdAt(), Instant.now(), operation.activatedAt()));
        return operationViewWithImpact(companyId, userId, updated);
    }

    public OperationView activate(
            String companyId,
            String userId,
            Long workspaceId,
            String operationId) {
        Operation operation = requireOperation(companyId, workspaceId, operationId);
        requireRequester(operation, userId);
        if (blank(operation.approvalRequestId())) {
            throw new ConflictException("SEMATTICE_APPROVAL_REQUIRED");
        }
        OntologyWorkspaceEntity workspace = requireWorkspace(companyId, workspaceId);
        if (!Objects.equals(workspace.getDraftRevision(), operation.sourceRevision())) {
            throw new ConflictException("ONTOLOGY_REVISION_CONFLICT");
        }
        try {
            Operation progressed = switch (operation.operationType()) {
                case "INITIAL_PUBLISH" -> activateInitial(companyId, userId, operation);
                case "ROLLBACK" -> activateRollback(companyId, userId, operation);
                default -> activateChangeset(companyId, userId, operation);
            };
            if ("ACTIVE".equals(progressed.status())) {
                publisher.publish(companyId, userId, workspaceId, operation.sourceRevision());
                syncBinding(companyId, userId, workspaceId);
            } else if ("ROLLED_BACK".equals(progressed.status())) {
                publisher.rollbackToPrevious(companyId, userId, workspaceId);
                syncBinding(companyId, userId, workspaceId);
            }
            return operationViewWithImpact(companyId, userId, progressed);
        } catch (RuntimeException exception) {
            states.saveOperation(withState(operation, "FAILED", safeError(exception)));
            Binding binding = requireBinding(companyId, workspaceId);
            markBindingFailed(binding, "FAILED", safeError(exception));
            throw exception;
        }
    }

    public OperationView operation(
            String companyId,
            String userId,
            Long workspaceId,
            String operationId) {
        requireWorkspace(companyId, workspaceId);
        return operationViewWithImpact(
                companyId, userId, requireOperation(companyId, workspaceId, operationId));
    }

    public OperationView latestOperation(String companyId, String userId, Long workspaceId) {
        requireWorkspace(companyId, workspaceId);
        return states.findLatestOperation(companyId, workspaceId)
                .map(value -> operationViewWithImpact(companyId, userId, value))
                .orElse(null);
    }

    public OperationView cancel(
            String companyId,
            String userId,
            Long workspaceId,
            String operationId) {
        Operation operation = requireOperation(companyId, workspaceId, operationId);
        requireRequester(operation, userId);
        if (!"CHANGESET".equals(operation.operationType()) || blank(operation.changesetId())) {
            throw new ConflictException("SEMATTICE_CHANGESET_CANCEL_UNAVAILABLE");
        }
        gateway.invoke(
                companyId, userId, CHANGESET_CANCEL,
                Map.of("changeset_id", operation.changesetId()),
                operation.operationId() + ":cancel");
        Operation canceled = states.saveOperation(withState(operation, "CANCELED", null));
        syncBinding(companyId, userId, workspaceId);
        return operationViewWithImpact(companyId, userId, canceled);
    }

    public OperationView prepareRollback(
            String companyId,
            String userId,
            Long workspaceId,
            String activeOperationId) {
        requireWorkspace(companyId, workspaceId);
        Operation active = requireOperation(companyId, workspaceId, activeOperationId);
        if (!"CHANGESET".equals(active.operationType())
                || !"ACTIVE".equals(active.status())
                || active.requiresBackfill()
                || blank(active.changesetId())
                || blank(active.baseMetadataVersionId())) {
            throw new ConflictException("SEMATTICE_CHANGESET_ROLLBACK_UNAVAILABLE");
        }
        RemoteBundle remote = parseRemote(currentMetadata(companyId, userId, true));
        if (!Objects.equals(remote.versionId(), active.candidateMetadataVersionId())) {
            throw new ConflictException("SEMATTICE_METADATA_DRIFTED");
        }
        String digest = active.sourceDigest() + ":rollback:" + active.operationId();
        Operation existing = states.findOperationByRevision(
                        companyId, workspaceId, "ROLLBACK", active.sourceRevision(), digest)
                .orElse(null);
        if (existing != null) {
            return operationViewWithImpact(companyId, userId, existing);
        }
        Instant now = Instant.now();
        Operation rollback = states.saveOperation(new Operation(
                UUID.randomUUID().toString(), companyId, workspaceId, "ROLLBACK",
                active.sourceRevision(), digest,
                active.candidateMetadataVersionId(), active.baseMetadataVersionId(),
                active.changesetId(), "CHANGESET", active.changesetId(), null,
                "VALIDATED", "high", false, userId, null, null, now, now, null));
        return operationViewWithImpact(companyId, userId, rollback);
    }

    private Operation compileCandidate(
            String companyId,
            String userId,
            OntologyWorkspaceEntity workspace,
            Contract contract,
            RemoteBundle remote,
            Operation operation) {
        if (remote != null) {
            requireNoUnsupportedDeletion(contract, remote);
        }
        String candidateId = operation.candidateMetadataVersionId();
        if (blank(candidateId)) {
            JsonNode created = gateway.invoke(
                    companyId, userId, VERSION_CREATE, Map.of(), operation.operationId() + ":version");
            candidateId = required(created, "metadata_version_id");
            operation = states.saveOperation(new Operation(
                    operation.operationId(), operation.companyId(), operation.workspaceId(),
                    operation.operationType(), operation.sourceRevision(), operation.sourceDigest(),
                    operation.baseMetadataVersionId(), candidateId, operation.changesetId(),
                    operation.subjectType(), operation.subjectId(), operation.approvalRequestId(),
                    operation.status(), operation.riskLevel(), operation.requiresBackfill(),
                    operation.requestedBy(), operation.approvedBy(), operation.lastErrorCode(),
                    operation.createdAt(), Instant.now(), operation.activatedAt()));
        }
        Map<String, ElementBinding> bound = states.listElements(companyId, workspace.getId()).stream()
                .collect(Collectors.toMap(
                        element -> element.elementType() + ":" + element.elementKey(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, String> objectIds = new LinkedHashMap<>();
        for (ObjectDefinition definition : contract.objects()) {
            String existingId = identity(bound, remote, "CONCEPT", definition.elementKey(), definition.apiName(), null);
            Map<String, Object> input = objectInput(candidateId, existingId, definition);
            JsonNode saved = gateway.invoke(
                    companyId, userId, OBJECT_UPSERT, input,
                    operation.operationId() + ":object:" + definition.elementKey());
            String objectId = required(saved, "object_id");
            objectIds.put(definition.elementKey(), objectId);
            saveElement(workspace, operation, "CONCEPT", definition.elementKey(), objectId, definition.apiName());
        }
        for (FieldDefinition definition : contract.fields()) {
            String objectId = objectIds.get(definition.conceptKey());
            String existingId = identity(
                    bound, remote, "PROPERTY", definition.elementKey(), definition.apiName(), objectId);
            JsonNode saved = gateway.invoke(
                    companyId, userId, FIELD_UPSERT,
                    fieldInput(candidateId, existingId, objectId, definition),
                    operation.operationId() + ":field:" + definition.elementKey());
            saveElement(workspace, operation, "PROPERTY", definition.elementKey(),
                    required(saved, "field_id"), definition.apiName());
        }
        for (RelationDefinition definition : contract.relations()) {
            String existingId = identity(
                    bound, remote, "RELATION", definition.elementKey(), definition.apiName(), null);
            JsonNode saved = gateway.invoke(
                    companyId, userId, RELATION_UPSERT,
                    relationInput(candidateId, existingId, objectIds, definition),
                    operation.operationId() + ":relation:" + definition.elementKey());
            saveElement(workspace, operation, "RELATION", definition.elementKey(),
                    required(saved, "relation_id"), definition.apiName());
        }
        if (remote == null) {
            return states.saveOperation(new Operation(
                    operation.operationId(), operation.companyId(), operation.workspaceId(),
                    "INITIAL_PUBLISH", operation.sourceRevision(), operation.sourceDigest(),
                    null, candidateId, null, "METADATA_VERSION", candidateId,
                    operation.approvalRequestId(), "VALIDATED", "high", false,
                    operation.requestedBy(), null, null, operation.createdAt(), Instant.now(), null));
        }
        JsonNode changeset = gateway.invoke(
                companyId, userId, CHANGESET_VALIDATE,
                Map.of("candidate_metadata_version_id", candidateId),
                operation.operationId() + ":changeset:validate");
        String changesetId = required(changeset, "changeset_id");
        return states.saveOperation(new Operation(
                operation.operationId(), operation.companyId(), operation.workspaceId(),
                "CHANGESET", operation.sourceRevision(), operation.sourceDigest(),
                remote.versionId(), candidateId, changesetId, "CHANGESET", changesetId,
                operation.approvalRequestId(), "VALIDATED",
                changeset.path("risk_level").asText("medium"),
                changeset.path("requires_backfill").asBoolean(false),
                operation.requestedBy(), null, null, operation.createdAt(), Instant.now(), null));
    }

    private Operation activateInitial(String companyId, String userId, Operation operation) {
        gateway.invoke(
                companyId, userId, VERSION_PUBLISH,
                Map.of(
                        "metadata_version_id", operation.candidateMetadataVersionId(),
                        "approval_id", operation.approvalRequestId()),
                operation.operationId() + ":publish");
        return states.saveOperation(active(operation));
    }

    private Operation activateChangeset(String companyId, String userId, Operation operation) {
        JsonNode state = gateway.invoke(
                companyId, userId, CHANGESET_APPROVE,
                Map.of(
                        "changeset_id", operation.changesetId(),
                        "approval_id", operation.approvalRequestId()),
                operation.operationId() + ":approve");
        String remoteState = state.path("state").asText();
        Operation progressed = states.saveOperation(withState(operation, "APPROVED", null));
        if (operation.requiresBackfill() && !"ready".equals(remoteState)) {
            JsonNode batch = gateway.invoke(
                    companyId, userId, CHANGESET_BACKFILL,
                    Map.of("changeset_id", operation.changesetId(), "batch_size", BACKFILL_BATCH_SIZE),
                    operation.operationId() + ":backfill:" + Instant.now().toEpochMilli());
            progressed = states.saveOperation(withState(progressed, "BACKFILLING", null));
            if (batch.path("remaining_records").asLong(1) > 0) {
                return progressed;
            }
            JsonNode covered = gateway.invoke(
                    companyId, userId, CHANGESET_COVERAGE,
                    Map.of("changeset_id", operation.changesetId()),
                    operation.operationId() + ":coverage");
            if (!"ready".equals(covered.path("state").asText())) {
                return progressed;
            }
            progressed = states.saveOperation(withState(progressed, "READY", null));
        }
        JsonNode current = gateway.invokeRead(
                companyId, userId, CHANGESET_STATUS, Map.of("changeset_id", operation.changesetId()));
        String checkedState = current.path("state").asText();
        if (operation.requiresBackfill() && !"ready".equals(checkedState)) {
            return progressed;
        }
        gateway.invoke(
                companyId, userId, CHANGESET_PUBLISH,
                Map.of("changeset_id", operation.changesetId()),
                operation.operationId() + ":publish");
        return states.saveOperation(active(progressed));
    }

    private Operation activateRollback(String companyId, String userId, Operation operation) {
        gateway.invoke(
                companyId, userId, CHANGESET_ROLLBACK,
                Map.of(
                        "changeset_id", operation.changesetId(),
                        "approval_id", operation.approvalRequestId()),
                operation.operationId() + ":rollback");
        Instant now = Instant.now();
        return states.saveOperation(new Operation(
                operation.operationId(), operation.companyId(), operation.workspaceId(),
                operation.operationType(), operation.sourceRevision(), operation.sourceDigest(),
                operation.baseMetadataVersionId(), operation.candidateMetadataVersionId(),
                operation.changesetId(), operation.subjectType(), operation.subjectId(),
                operation.approvalRequestId(), "ROLLED_BACK", operation.riskLevel(), false,
                operation.requestedBy(), operation.approvedBy(), null,
                operation.createdAt(), now, now));
    }

    private void syncBinding(String companyId, String userId, Long workspaceId) {
        RemoteBundle remote = parseRemote(currentMetadata(companyId, userId, true));
        Binding binding = requireBinding(companyId, workspaceId);
        Instant now = Instant.now();
        states.saveBinding(new Binding(
                companyId, workspaceId, binding.sematticeTenantId(), remote.versionId(),
                remote.sequence(), remote.digest(), "IN_SYNC", null, now,
                binding.createdAt(), now));
    }

    private void requireNoUnsupportedDeletion(Contract contract, RemoteBundle remote) {
        Set<String> objectApis = contract.objects().stream()
                .map(ObjectDefinition::apiName).collect(Collectors.toSet());
        if (!objectApis.containsAll(remote.objectIdsByApi().keySet())) {
            throw new ConflictException("SEMATTICE_OBJECT_DELETION_NOT_SUPPORTED");
        }
        Set<String> fieldApis = contract.fields().stream()
                .map(field -> contract.objects().stream()
                        .filter(object -> object.elementKey().equals(field.conceptKey()))
                        .findFirst().orElseThrow().apiName() + "." + field.apiName())
                .collect(Collectors.toSet());
        if (!fieldApis.containsAll(remote.fieldIdsByObjectApiAndFieldApi().keySet())) {
            throw new ConflictException("SEMATTICE_FIELD_DELETION_REQUIRES_TOMBSTONE");
        }
        Set<String> relationApis = contract.relations().stream()
                .map(RelationDefinition::apiName).collect(Collectors.toSet());
        if (!relationApis.containsAll(remote.relationIdsByApi().keySet())) {
            throw new ConflictException("SEMATTICE_RELATION_DELETION_NOT_SUPPORTED");
        }
    }

    private void bindMatchingElements(OntologyWorkspaceEntity workspace, RemoteBundle remote) {
        OntologyDocument document = drafts.loadDraft(
                workspace.getCompanyId(), workspace.getId(), workspace);
        if (document.concepts() == null
                || document.concepts().stream().noneMatch(OntologyDocument.Concept::enabled)) {
            return;
        }
        Contract contract = compiler.compile(workspace.getId(), workspace.getDraftRevision(), document);
        for (ObjectDefinition object : contract.objects()) {
            String id = remote.objectIdsByApi().get(object.apiName());
            if (id != null) {
                saveElement(workspace, workspace.getDraftRevision(), remote.digest(),
                        "CONCEPT", object.elementKey(), id, object.apiName());
            }
        }
        Map<String, String> objectApiByKey = contract.objects().stream()
                .collect(Collectors.toMap(ObjectDefinition::elementKey, ObjectDefinition::apiName));
        for (FieldDefinition field : contract.fields()) {
            String objectApi = objectApiByKey.get(field.conceptKey());
            String id = remote.fieldIdsByObjectApiAndFieldApi().get(objectApi + "." + field.apiName());
            if (id != null) {
                saveElement(workspace, workspace.getDraftRevision(), remote.digest(),
                        "PROPERTY", field.elementKey(), id, field.apiName());
            }
        }
        for (RelationDefinition relation : contract.relations()) {
            String id = remote.relationIdsByApi().get(relation.apiName());
            if (id != null) {
                saveElement(workspace, workspace.getDraftRevision(), remote.digest(),
                        "RELATION", relation.elementKey(), id, relation.apiName());
            }
        }
    }

    private String identity(
            Map<String, ElementBinding> bound,
            RemoteBundle remote,
            String type,
            String key,
            String apiName,
            String objectId) {
        ElementBinding existing = bound.get(type + ":" + key);
        if (existing != null) {
            return existing.sematticeElementId();
        }
        if (remote == null) {
            return null;
        }
        return switch (type) {
            case "CONCEPT" -> remote.objectIdsByApi().get(apiName);
            case "PROPERTY" -> remote.fieldIdsByObjectIdAndFieldApi().get(objectId + "." + apiName);
            case "RELATION" -> remote.relationIdsByApi().get(apiName);
            default -> null;
        };
    }

    private Map<String, Object> objectInput(
            String versionId,
            String objectId,
            ObjectDefinition definition) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("metadata_version_id", versionId);
        putIfText(input, "object_id", objectId);
        input.put("api_name", definition.apiName());
        input.put("label", definition.label());
        input.put("description", definition.description());
        input.put("semantic", definition.semantic());
        return input;
    }

    private Map<String, Object> fieldInput(
            String versionId,
            String fieldId,
            String objectId,
            FieldDefinition definition) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("metadata_version_id", versionId);
        putIfText(input, "field_id", fieldId);
        input.put("object_id", objectId);
        input.put("api_name", definition.apiName());
        input.put("label", definition.label());
        input.put("description", definition.description());
        input.put("data_type", definition.dataType());
        input.put("required", definition.required());
        input.put("indexed", definition.indexed());
        input.put("unique_value", definition.uniqueValue());
        input.put("lifecycle_state", definition.lifecycleState());
        input.put("index_state", definition.indexState());
        input.put("default_semantics", definition.defaultSemantics());
        input.put("constraints", definition.constraints());
        input.put("semantic", definition.semantic());
        return input;
    }

    private Map<String, Object> relationInput(
            String versionId,
            String relationId,
            Map<String, String> objectIds,
            RelationDefinition definition) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("metadata_version_id", versionId);
        putIfText(input, "relation_id", relationId);
        input.put("api_name", definition.apiName());
        input.put("source_object_id", objectIds.get(definition.sourceConceptKey()));
        input.put("target_object_id", objectIds.get(definition.targetConceptKey()));
        input.put("relation_type", definition.relationType());
        input.put("delete_behavior", definition.deleteBehavior());
        input.put("description", definition.description());
        input.put("semantic", definition.semantic());
        return input;
    }

    private RemoteBundle parseRemote(JsonNode bundle) {
        JsonNode version = bundle.path("version");
        String versionId = required(version, "metadata_version_id");
        long sequence = version.path("sequence").asLong();
        String digest = version.path("snapshot_digest").asText("");
        Map<String, String> objectIdsByApi = new LinkedHashMap<>();
        Map<String, String> objectApisById = new LinkedHashMap<>();
        for (JsonNode object : bundle.path("objects")) {
            String api = required(object, "api_name");
            String id = required(object, "object_id");
            objectIdsByApi.put(api, id);
            objectApisById.put(id, api);
        }
        Map<String, String> fieldIdsByObjectApiAndFieldApi = new LinkedHashMap<>();
        Map<String, String> fieldIdsByObjectIdAndFieldApi = new LinkedHashMap<>();
        for (JsonNode field : bundle.path("fields")) {
            if ("tombstone".equalsIgnoreCase(field.path("lifecycle_state").asText())) {
                continue;
            }
            String objectId = required(field, "object_id");
            String api = required(field, "api_name");
            String id = required(field, "field_id");
            fieldIdsByObjectIdAndFieldApi.put(objectId + "." + api, id);
            String objectApi = objectApisById.get(objectId);
            if (objectApi != null) {
                fieldIdsByObjectApiAndFieldApi.put(objectApi + "." + api, id);
            }
        }
        Map<String, String> relationIdsByApi = new LinkedHashMap<>();
        for (JsonNode relation : bundle.path("relations")) {
            relationIdsByApi.put(required(relation, "api_name"), required(relation, "relation_id"));
        }
        return new RemoteBundle(
                versionId, sequence, digest,
                Map.copyOf(objectIdsByApi), Map.copyOf(fieldIdsByObjectApiAndFieldApi),
                Map.copyOf(fieldIdsByObjectIdAndFieldApi), Map.copyOf(relationIdsByApi));
    }

    private JsonNode currentMetadata(
            String companyId,
            String userId,
            boolean required) {
        try {
            return gateway.invokeRead(companyId, userId, GET_CURRENT, Map.of());
        } catch (SematticeCapabilityException exception) {
            if (!required && ("FAILED_PRECONDITION".equals(exception.code())
                    || "RESOURCE_NOT_FOUND".equals(exception.code()))) {
                return null;
            }
            throw exception;
        }
    }

    private String requireProvisionedTenant(String companyId) {
        return provisioningBindings.findByCompanyId(companyId)
                .filter(binding -> SematticeProvisioningBindingEntity.PROVISIONED.equals(binding.getState()))
                .map(SematticeProvisioningBindingEntity::getSematticeTenantId)
                .filter(value -> !blank(value))
                .orElseThrow(() -> new ConflictException("SEMATTICE_TENANT_NOT_PROVISIONED"));
    }

    private OntologyWorkspaceEntity requireWorkspace(String companyId, Long workspaceId) {
        return workspaces.findByIdAndCompanyId(workspaceId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("ONTOLOGY_NOT_FOUND"));
    }

    private Binding requireBinding(String companyId, Long workspaceId) {
        return states.findBinding(companyId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("SEMATTICE_ONTOLOGY_NOT_LINKED"));
    }

    private Operation requireOperation(String companyId, Long workspaceId, String operationId) {
        return states.findOperation(companyId, workspaceId, operationId)
                .orElseThrow(() -> new ResourceNotFoundException("SEMATTICE_ONTOLOGY_OPERATION_NOT_FOUND"));
    }

    private void requireRequester(Operation operation, String userId) {
        if (!Objects.equals(operation.requestedBy(), userId)) {
            throw new ForbiddenException("SEMATTICE_ONTOLOGY_OPERATION_REQUESTER_REQUIRED");
        }
    }

    private void saveElement(
            OntologyWorkspaceEntity workspace,
            Operation operation,
            String type,
            String key,
            String id,
            String apiName) {
        saveElement(workspace, operation.sourceRevision(), operation.sourceDigest(), type, key, id, apiName);
    }

    private void saveElement(
            OntologyWorkspaceEntity workspace,
            long revision,
            String digest,
            String type,
            String key,
            String id,
            String apiName) {
        Instant now = Instant.now();
        ElementBinding existing = states.listElements(
                        workspace.getCompanyId(), workspace.getId()).stream()
                .filter(value -> value.elementType().equals(type) && value.elementKey().equals(key))
                .findFirst().orElse(null);
        states.saveElement(new ElementBinding(
                workspace.getCompanyId(), workspace.getId(), type, key, id, apiName,
                existing == null ? revision : existing.firstBoundRevision(), revision,
                blank(digest) ? "remote:" + id : digest,
                "ACTIVE", existing == null ? now : existing.createdAt(), now));
    }

    private void markBindingFailed(Binding binding, String status, String error) {
        Instant now = Instant.now();
        states.saveBinding(new Binding(
                binding.companyId(), binding.workspaceId(), binding.sematticeTenantId(),
                binding.activeMetadataVersionId(), binding.activeSequence(), binding.activeDigest(),
                status, error, now, binding.createdAt(), now));
    }

    private Operation withState(Operation operation, String status, String error) {
        return new Operation(
                operation.operationId(), operation.companyId(), operation.workspaceId(),
                operation.operationType(), operation.sourceRevision(), operation.sourceDigest(),
                operation.baseMetadataVersionId(), operation.candidateMetadataVersionId(),
                operation.changesetId(), operation.subjectType(), operation.subjectId(),
                operation.approvalRequestId(), status, operation.riskLevel(),
                operation.requiresBackfill(), operation.requestedBy(), operation.approvedBy(),
                error, operation.createdAt(), Instant.now(), operation.activatedAt());
    }

    private Operation active(Operation operation) {
        Instant now = Instant.now();
        return new Operation(
                operation.operationId(), operation.companyId(), operation.workspaceId(),
                operation.operationType(), operation.sourceRevision(), operation.sourceDigest(),
                operation.baseMetadataVersionId(), operation.candidateMetadataVersionId(),
                operation.changesetId(), operation.subjectType(), operation.subjectId(),
                operation.approvalRequestId(), "ACTIVE", operation.riskLevel(),
                operation.requiresBackfill(), operation.requestedBy(), operation.approvedBy(),
                null, operation.createdAt(), now, now);
    }

    private OntologyDocument.Property toProperty(JsonNode field) {
        String key = field.path("api_name").asText();
        JsonNode semantic = field.path("semantic");
        return new OntologyDocument.Property(
                key,
                nonBlank(field.path("label").asText(), key),
                field.path("description").asText(),
                sourceDataType(field.path("data_type").asText(), semantic.path("source_data_type").asText()),
                field.path("required").asBoolean(false),
                semantic.path("multiple").asBoolean(false),
                "sensitive".equalsIgnoreCase(semantic.path("sensitivity").asText()),
                semantic.path("queryable").asBoolean(field.path("indexed").asBoolean(false)),
                enumValues(field.path("constraints").path("enum")));
    }

    private OntologyDocument.DataType sourceDataType(String remote, String source) {
        if (!blank(source)) {
            try {
                return OntologyDocument.DataType.valueOf(source.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Fall back to the executable type.
            }
        }
        return switch (remote.toLowerCase(Locale.ROOT)) {
            case "number" -> OntologyDocument.DataType.DECIMAL;
            case "boolean" -> OntologyDocument.DataType.BOOLEAN;
            case "date" -> OntologyDocument.DataType.DATE;
            case "datetime" -> OntologyDocument.DataType.DATETIME;
            case "uuid" -> OntologyDocument.DataType.REFERENCE;
            default -> OntologyDocument.DataType.TEXT;
        };
    }

    private OntologyDocument.ConceptType conceptType(JsonNode semantic) {
        return "EVENT".equalsIgnoreCase(semantic.path("concept_type").asText())
                ? OntologyDocument.ConceptType.EVENT
                : OntologyDocument.ConceptType.ENTITY;
    }

    private OntologyDocument.Cardinality cardinality(JsonNode relation) {
        String semantic = relation.path("semantic").path("cardinality").asText();
        if (!blank(semantic)) {
            try {
                return OntologyDocument.Cardinality.valueOf(semantic);
            } catch (IllegalArgumentException ignored) {
                // Fall through to executable relation type.
            }
        }
        return "many_to_many".equals(relation.path("relation_type").asText())
                ? OntologyDocument.Cardinality.MANY_TO_MANY
                : OntologyDocument.Cardinality.MANY_TO_ONE;
    }

    private List<String> enumValues(JsonNode value) {
        List<String> result = new ArrayList<>();
        if (value.isArray()) {
            value.forEach(item -> {
                if (item.isTextual()) {
                    result.add(item.asText());
                }
            });
        }
        return List.copyOf(result);
    }

    private OntologyDocument sanitize(OntologyDocument document) {
        try {
            return objectMapper.treeToValue(objectMapper.valueToTree(document), OntologyDocument.class);
        } catch (Exception exception) {
            throw new IllegalStateException("SEMATTICE_IMPORT_PROPOSAL_INVALID", exception);
        }
    }

    private BindingView bindingView(Binding value) {
        return new BindingView(
                value.workspaceId(), value.syncStatus(), value.activeMetadataVersionId(),
                value.activeSequence(), value.activeDigest(), value.lastErrorCode(),
                value.lastCheckedAt(), states.listElements(value.companyId(), value.workspaceId()).size());
    }

    private OperationView operationView(Operation value) {
        return new OperationView(
                value.operationId(), value.operationType(), value.sourceRevision(),
                value.sourceDigest(), value.baseMetadataVersionId(),
                value.candidateMetadataVersionId(), value.changesetId(), value.subjectType(),
                value.subjectId(), value.approvalRequestId(), value.status(), value.riskLevel(),
                value.requiresBackfill(), value.lastErrorCode(), value.updatedAt(), value.activatedAt(),
                null, null, null);
    }

    private OperationView operationViewWithImpact(
            String companyId,
            String userId,
            Operation value) {
        OperationView base = operationView(value);
        if (blank(value.changesetId())) {
            return base;
        }
        JsonNode impact = gateway.invokeRead(
                companyId, userId, CHANGESET_SIMULATE,
                Map.of("changeset_id", value.changesetId()));
        return new OperationView(
                base.operationId(), base.operationType(), base.sourceRevision(), base.sourceDigest(),
                base.baseMetadataVersionId(), base.candidateMetadataVersionId(), base.changesetId(),
                base.subjectType(), base.subjectId(), base.approvalRequestId(), base.status(),
                base.riskLevel(), base.requiresBackfill(), base.lastErrorCode(), base.updatedAt(),
                base.activatedAt(), nullableNode(impact.path("plan")),
                nullableNode(impact.path("simulation")), nullableNode(impact.path("coverage")));
    }

    private JsonNode nullableNode(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull() ? null : value.deepCopy();
    }

    private String required(JsonNode value, String field) {
        String result = value.path(field).asText("");
        if (result.isBlank()) {
            throw new SematticeCapabilityException(
                    "SEMATTICE_RESPONSE_INVALID", "Semattice response is missing " + field);
        }
        return result;
    }

    private String safeError(Throwable exception) {
        if (exception instanceof SematticeCapabilityException capability) {
            return capability.code();
        }
        String message = exception.getMessage();
        return message != null && message.matches("[A-Z][A-Z0-9_]{2,63}")
                ? message
                : "SEMATTICE_ONTOLOGY_OPERATION_FAILED";
    }

    private void putIfText(Map<String, Object> target, String key, String value) {
        if (!blank(value)) {
            target.put(key, value);
        }
    }

    private static String nonBlank(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record BindingView(
            Long workspaceId,
            String syncStatus,
            String activeMetadataVersionId,
            Long activeSequence,
            String activeDigest,
            String lastErrorCode,
            Instant lastCheckedAt,
            int boundElements) {
    }

    public record ImportProposal(
            long expectedRevision,
            String metadataVersionId,
            long metadataSequence,
            String metadataDigest,
            OntologyDocument candidate,
            ImportDiff diff) {
    }

    public record ImportDiff(int objects, int relations, int fields) {
    }

    public record OperationView(
            String operationId,
            String operationType,
            long sourceRevision,
            String sourceDigest,
            String baseMetadataVersionId,
            String candidateMetadataVersionId,
            String changesetId,
            String subjectType,
            String subjectId,
            String approvalRequestId,
            String status,
            String riskLevel,
            boolean requiresBackfill,
            String lastErrorCode,
            Instant updatedAt,
            Instant activatedAt,
            JsonNode plan,
            JsonNode simulation,
            JsonNode coverage) {
    }

    private record RemoteBundle(
            String versionId,
            long sequence,
            String digest,
            Map<String, String> objectIdsByApi,
            Map<String, String> fieldIdsByObjectApiAndFieldApi,
            Map<String, String> fieldIdsByObjectIdAndFieldApi,
            Map<String, String> relationIdsByApi) {
    }
}
