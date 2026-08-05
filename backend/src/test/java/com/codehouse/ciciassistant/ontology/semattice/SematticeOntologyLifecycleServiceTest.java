package com.codehouse.ciciassistant.ontology.semattice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.auth.domain.SematticeProvisioningBindingEntity;
import com.codehouse.ciciassistant.auth.domain.SematticeProvisioningBindingRepository;
import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.ontology.service.OntologyDraftService;
import com.codehouse.ciciassistant.ontology.service.OntologyPublishService;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyStateStore.Binding;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyStateStore.ElementBinding;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyStateStore.Operation;
import com.codehouse.ciciassistant.semattice.SematticeMetadataApprovalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SematticeOntologyLifecycleServiceTest {

    private static final String COMPANY_ID = "org-test";
    private static final String USER_ID = "member-requester";
    private static final Long WORKSPACE_ID = 42L;
    private static final String VERSION_ID = "11111111-1111-4111-8111-111111111111";
    private static final String OBJECT_ID = "22222222-2222-4222-8222-222222222222";
    private static final String FIELD_ID = "33333333-3333-4333-8333-333333333333";
    private static final String APPROVAL_ID = "44444444-4444-4444-8444-444444444444";
    private static final String BASE_VERSION_ID = "55555555-5555-4555-8555-555555555555";
    private static final String CHANGESET_ID = "66666666-6666-4666-8666-666666666666";
    private static final String ROLLBACK_APPROVAL_ID = "77777777-7777-4777-8777-777777777777";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OntologyWorkspaceRepository workspaces = mock(OntologyWorkspaceRepository.class);
    private final OntologyDraftService drafts = mock(OntologyDraftService.class);
    private final OntologyPublishService publisher = mock(OntologyPublishService.class);
    private final SematticeProvisioningBindingRepository provisioning =
            mock(SematticeProvisioningBindingRepository.class);
    private final SematticeMetadataApprovalService approvals = mock(SematticeMetadataApprovalService.class);
    private final InMemoryStateStore states = new InMemoryStateStore();
    private final FakeGateway gateway = new FakeGateway(objectMapper);
    private OntologyWorkspaceEntity workspace;
    private SematticeOntologyLifecycleService service;

    @BeforeEach
    void setUp() {
        workspace = new OntologyWorkspaceEntity(
                COMPANY_ID, "project-delivery", "项目交付", "统一项目语义", USER_ID);
        ReflectionTestUtils.setField(workspace, "id", WORKSPACE_ID);
        ReflectionTestUtils.setField(workspace, "draftRevision", 3L);
        when(workspaces.findByIdAndCompanyId(WORKSPACE_ID, COMPANY_ID))
                .thenReturn(Optional.of(workspace));
        when(drafts.loadDraft(COMPANY_ID, WORKSPACE_ID, workspace)).thenReturn(document());
        SematticeProvisioningBindingEntity provisioned = new SematticeProvisioningBindingEntity(
                "reservation", COMPANY_ID, "request");
        provisioned.complete("tenant-1", "operation-1", true, null);
        when(provisioning.findByCompanyId(COMPANY_ID)).thenReturn(Optional.of(provisioned));
        service = new SematticeOntologyLifecycleService(
                workspaces, drafts, publisher, provisioning, gateway,
                new SematticeOntologyContractCompiler(objectMapper), states,
                approvals, objectMapper);
    }

    @Test
    void linksUninitializedTenantThenPublishesOneIdempotentApprovedInitialVersion() {
        gateway.currentAvailable = false;

        var linked = service.link(COMPANY_ID, USER_ID, WORKSPACE_ID);
        assertThat(linked.syncStatus()).isEqualTo("LINKED");
        assertThat(linked.activeMetadataVersionId()).isNull();

        var compiled = service.prepare(COMPANY_ID, USER_ID, WORKSPACE_ID, 3L);
        var replay = service.prepare(COMPANY_ID, USER_ID, WORKSPACE_ID, 3L);

        assertThat(compiled.operationId()).isEqualTo(replay.operationId());
        assertThat(compiled.operationType()).isEqualTo("INITIAL_PUBLISH");
        assertThat(compiled.status()).isEqualTo("VALIDATED");
        assertThat(gateway.callCount("metadata.version.create")).isEqualTo(1);
        assertThat(states.listElements(COMPANY_ID, WORKSPACE_ID))
                .extracting(ElementBinding::elementType)
                .containsExactlyInAnyOrder("CONCEPT", "PROPERTY");

        when(approvals.request(
                COMPANY_ID, USER_ID, "METADATA_VERSION", VERSION_ID, "发布业务本体工作区 42，来源修订 3"))
                .thenReturn(new SematticeMetadataApprovalService.ApprovalView(
                        APPROVAL_ID, "METADATA_VERSION", VERSION_ID, "首次发布",
                        USER_ID, null, "PENDING", null, Instant.now(), null));
        var pending = service.requestApproval(
                COMPANY_ID, USER_ID, WORKSPACE_ID, compiled.operationId());
        assertThat(pending.status()).isEqualTo("APPROVAL_PENDING");

        gateway.currentAvailable = true;
        var activated = service.activate(
                COMPANY_ID, USER_ID, WORKSPACE_ID, compiled.operationId());

        assertThat(activated.status()).isEqualTo("ACTIVE");
        assertThat(service.status(COMPANY_ID, WORKSPACE_ID).syncStatus()).isEqualTo("IN_SYNC");
        verify(publisher, times(1)).publish(COMPANY_ID, USER_ID, WORKSPACE_ID, 3L);
        assertThat(gateway.callCount("metadata.version.publish")).isEqualTo(1);
    }

    @Test
    void detectsRemoteVersionDriftBeforeCreatingCandidate() {
        gateway.currentAvailable = true;
        gateway.currentVersionId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
        service.link(COMPANY_ID, USER_ID, WORKSPACE_ID);
        gateway.currentVersionId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

        assertThatThrownBy(() -> service.prepare(COMPANY_ID, USER_ID, WORKSPACE_ID, 3L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("SEMATTICE_METADATA_DRIFTED");

        assertThat(service.status(COMPANY_ID, WORKSPACE_ID).syncStatus()).isEqualTo("DRIFTED");
        assertThat(gateway.callCount("metadata.version.create")).isZero();
    }

    @Test
    void reportsExplicitNotLinkedStatusWithoutCreatingPersistenceState() {
        var status = service.status(COMPANY_ID, WORKSPACE_ID);

        assertThat(status.syncStatus()).isEqualTo("NOT_LINKED");
        assertThat(status.boundElements()).isZero();
        assertThat(states.findBinding(COMPANY_ID, WORKSPACE_ID)).isEmpty();
    }

    @Test
    void simulatesCancelsAndIndependentlyRollsBackSafeChangeset() {
        gateway.currentAvailable = true;
        gateway.currentVersionId = BASE_VERSION_ID;
        service.link(COMPANY_ID, USER_ID, WORKSPACE_ID);

        var compiled = service.prepare(COMPANY_ID, USER_ID, WORKSPACE_ID, 3L);
        assertThat(compiled.operationType()).isEqualTo("CHANGESET");
        assertThat(compiled.plan()).isNotNull();
        assertThat(compiled.simulation()).isNotNull();

        var canceled = service.cancel(COMPANY_ID, USER_ID, WORKSPACE_ID, compiled.operationId());
        assertThat(canceled.status()).isEqualTo("CANCELED");
        assertThat(service.status(COMPANY_ID, WORKSPACE_ID).syncStatus()).isEqualTo("IN_SYNC");

        InMemoryStateStore rollbackStates = new InMemoryStateStore();
        FakeGateway rollbackGateway = new FakeGateway(objectMapper);
        rollbackGateway.currentAvailable = true;
        rollbackGateway.currentVersionId = BASE_VERSION_ID;
        SematticeOntologyLifecycleService rollbackService = new SematticeOntologyLifecycleService(
                workspaces, drafts, publisher, provisioning, rollbackGateway,
                new SematticeOntologyContractCompiler(objectMapper), rollbackStates,
                approvals, objectMapper);
        rollbackService.link(COMPANY_ID, USER_ID, WORKSPACE_ID);
        var change = rollbackService.prepare(COMPANY_ID, USER_ID, WORKSPACE_ID, 3L);
        when(approvals.request(
                COMPANY_ID, USER_ID, "CHANGESET", CHANGESET_ID, "发布业务本体工作区 42，来源修订 3"))
                .thenReturn(new SematticeMetadataApprovalService.ApprovalView(
                        APPROVAL_ID, "CHANGESET", CHANGESET_ID, "发布",
                        USER_ID, null, "PENDING", null, Instant.now(), null));
        var publishPending = rollbackService.requestApproval(
                COMPANY_ID, USER_ID, WORKSPACE_ID, change.operationId());
        var active = rollbackService.activate(
                COMPANY_ID, USER_ID, WORKSPACE_ID, publishPending.operationId());
        assertThat(active.status()).isEqualTo("ACTIVE");

        var rollback = rollbackService.prepareRollback(
                COMPANY_ID, USER_ID, WORKSPACE_ID, active.operationId());
        assertThat(rollback.operationType()).isEqualTo("ROLLBACK");
        when(approvals.request(
                COMPANY_ID, USER_ID, "CHANGESET", CHANGESET_ID, "回滚业务本体工作区 42，来源修订 3"))
                .thenReturn(new SematticeMetadataApprovalService.ApprovalView(
                        ROLLBACK_APPROVAL_ID, "CHANGESET", CHANGESET_ID, "回滚",
                        USER_ID, null, "PENDING", null, Instant.now(), null));
        var rollbackPending = rollbackService.requestApproval(
                COMPANY_ID, USER_ID, WORKSPACE_ID, rollback.operationId());
        var rolledBack = rollbackService.activate(
                COMPANY_ID, USER_ID, WORKSPACE_ID, rollbackPending.operationId());

        assertThat(rolledBack.status()).isEqualTo("ROLLED_BACK");
        assertThat(rollbackService.status(COMPANY_ID, WORKSPACE_ID).activeMetadataVersionId())
                .isEqualTo(BASE_VERSION_ID);
        verify(publisher).rollbackToPrevious(COMPANY_ID, USER_ID, WORKSPACE_ID);
    }

    private OntologyDocument document() {
        OntologyDocument.Property name = new OntologyDocument.Property(
                "name", "项目名称", "业务项目名称", OntologyDocument.DataType.TEXT,
                true, false, false, true, List.of());
        OntologyDocument.Concept project = new OntologyDocument.Concept(
                "project", "项目", "项目", "交付项目", OntologyDocument.ConceptType.ENTITY,
                "name", 120, 120, true, true, List.of(name));
        return new OntologyDocument(
                "project-delivery", "项目交付", "统一项目语义",
                List.of(project), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static final class InMemoryStateStore implements SematticeOntologyStateStore {
        private Binding binding;
        private final Map<String, ElementBinding> elements = new LinkedHashMap<>();
        private final Map<String, Operation> operations = new LinkedHashMap<>();

        @Override
        public Optional<Binding> findBinding(String companyId, Long workspaceId) {
            return Optional.ofNullable(binding).filter(value ->
                    value.companyId().equals(companyId) && value.workspaceId().equals(workspaceId));
        }

        @Override
        public Binding saveBinding(Binding value) {
            binding = value;
            return value;
        }

        @Override
        public List<ElementBinding> listElements(String companyId, Long workspaceId) {
            return elements.values().stream()
                    .filter(value -> value.companyId().equals(companyId)
                            && value.workspaceId().equals(workspaceId))
                    .toList();
        }

        @Override
        public ElementBinding saveElement(ElementBinding value) {
            elements.put(value.elementType() + ":" + value.elementKey(), value);
            return value;
        }

        @Override
        public Optional<Operation> findOperation(String companyId, Long workspaceId, String operationId) {
            return Optional.ofNullable(operations.get(operationId)).filter(value ->
                    value.companyId().equals(companyId) && value.workspaceId().equals(workspaceId));
        }

        @Override
        public Optional<Operation> findLatestOperation(String companyId, Long workspaceId) {
            return new ArrayList<>(operations.values()).reversed().stream()
                    .filter(value -> value.companyId().equals(companyId)
                            && value.workspaceId().equals(workspaceId))
                    .findFirst();
        }

        @Override
        public Optional<Operation> findOperationByRevision(
                String companyId,
                Long workspaceId,
                String operationType,
                long sourceRevision,
                String sourceDigest) {
            return operations.values().stream().filter(value ->
                            value.companyId().equals(companyId)
                                    && value.workspaceId().equals(workspaceId)
                                    && value.operationType().equals(operationType)
                                    && value.sourceRevision() == sourceRevision
                                    && value.sourceDigest().equals(sourceDigest))
                    .findFirst();
        }

        @Override
        public Operation saveOperation(Operation value) {
            operations.put(value.operationId(), value);
            return value;
        }
    }

    private static final class FakeGateway implements SematticeOntologyGateway {
        private final ObjectMapper objectMapper;
        private final Map<String, Integer> calls = new LinkedHashMap<>();
        private boolean currentAvailable;
        private String currentVersionId = VERSION_ID;
        private String changesetBaseVersionId;
        private String changesetState = "validated";

        private FakeGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public JsonNode invoke(
                String companyId,
                String userId,
                String capabilityId,
                Map<String, Object> input,
                String idempotencyKey) {
            calls.merge(capabilityId, 1, Integer::sum);
            return switch (capabilityId) {
                case "metadata.version.create" -> json(Map.of("metadata_version_id", VERSION_ID));
                case "metadata.object.upsert" -> json(Map.of("object_id", OBJECT_ID));
                case "metadata.field.upsert" -> json(Map.of("field_id", FIELD_ID));
                case "metadata.changeset.validate" -> {
                    changesetBaseVersionId = currentVersionId;
                    changesetState = "validated";
                    yield changeset();
                }
                case "metadata.changeset.cancel" -> {
                    changesetState = "canceled";
                    yield changeset();
                }
                case "metadata.changeset.approve" -> {
                    changesetState = "approved";
                    yield changeset();
                }
                case "metadata.changeset.publish" -> {
                    changesetState = "active";
                    currentVersionId = VERSION_ID;
                    yield changeset();
                }
                case "metadata.changeset.rollback" -> {
                    changesetState = "rolled_back";
                    currentVersionId = changesetBaseVersionId;
                    yield changeset();
                }
                case "metadata.version.publish" -> {
                    currentAvailable = true;
                    currentVersionId = VERSION_ID;
                    yield json(Map.of("metadata_version_id", VERSION_ID, "state", "active"));
                }
                default -> throw new AssertionError("Unexpected capability " + capabilityId);
            };
        }

        @Override
        public JsonNode invokeRead(
                String companyId,
                String userId,
                String capabilityId,
                Map<String, Object> input) {
            calls.merge(capabilityId, 1, Integer::sum);
            if ("metadata.changeset.simulate".equals(capabilityId)
                    || "metadata.changeset.get-status".equals(capabilityId)) {
                return changeset();
            }
            if (!"metadata.version.get-current".equals(capabilityId)) {
                throw new AssertionError("Unexpected read capability " + capabilityId);
            }
            if (!currentAvailable) {
                throw new SematticeCapabilityException("FAILED_PRECONDITION", "No current metadata");
            }
            return json(Map.of(
                    "version", Map.of(
                            "metadata_version_id", currentVersionId,
                            "sequence", 1,
                            "snapshot_digest", "digest-1"),
                    "objects", List.of(Map.of(
                            "object_id", OBJECT_ID,
                            "api_name", "project",
                            "label", "项目")),
                    "fields", List.of(Map.of(
                            "field_id", FIELD_ID,
                            "object_id", OBJECT_ID,
                            "api_name", "name",
                            "label", "项目名称",
                            "lifecycle_state", "active")),
                    "relations", List.of()));
        }

        private JsonNode json(Object value) {
            return objectMapper.valueToTree(value);
        }

        private JsonNode changeset() {
            return json(Map.of(
                    "changeset_id", CHANGESET_ID,
                    "state", changesetState,
                    "risk_level", "low",
                    "requires_backfill", false,
                    "plan", Map.of("changes", List.of(Map.of(
                            "kind", "field_added",
                            "api_name", "name",
                            "eligible_records", 0))),
                    "simulation", Map.of("objects", List.of(Map.of(
                            "object_id", OBJECT_ID,
                            "record_count", 0,
                            "projected_typed_rows", 0))),
                    "coverage", Map.of()));
        }

        private int callCount(String capabilityId) {
            return calls.getOrDefault(capabilityId, 0);
        }
    }
}
