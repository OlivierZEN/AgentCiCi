package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ai.service.AliyunBailianClient;
import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ChatCompletionResult;
import com.codehouse.ciciassistant.ai.service.ModelRouterService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.codehouse.ciciassistant.ontology.domain.OntologyAiProposalEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyAiProposalRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OntologyAiProposalServiceTest {

    @Mock
    private OntologyWorkspaceRepository workspaces;
    @Mock
    private OntologyAiProposalRepository proposals;
    @Mock
    private OntologyPhysicalObjectRepository physicalObjects;
    @Mock
    private OntologyPhysicalFieldRepository physicalFields;
    @Mock
    private OntologyDraftService drafts;
    @Mock
    private OntologyValidationService validation;
    @Mock
    private ModelRouterService modelRouter;
    @Mock
    private ModelProviderService modelProviders;
    @Mock
    private AliyunBailianClient modelClient;
    @Mock
    private OntologyTenantPersistence persistence;

    private ObjectMapper objectMapper;
    private OntologyAiProposalService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new OntologyAiProposalService(
                workspaces,
                proposals,
                physicalObjects,
                physicalFields,
                drafts,
                validation,
                modelRouter,
                modelProviders,
                modelClient,
                persistence,
                objectMapper);
        TenantContext.setOrgId("org-a");
        TenantContext.setUserId("user-a");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void storesValidatedProposalWithoutChangingDraft() throws Exception {
        OntologyWorkspaceEntity workspace = workspace(41L, 3L);
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        when(workspaces.findByIdAndOrgId(41L, "org-a")).thenReturn(Optional.of(workspace));
        when(drafts.loadDraft("org-a", 41L, workspace)).thenReturn(current);
        when(modelRouter.route("org-a", "ontology-modeling")).thenReturn(Map.of(
                "provider", "provider-a",
                "modelName", "model-a"));
        when(modelProviders.credentialsForProvider("org-a", "provider-a")).thenReturn(Map.of(
                "enabled", "true",
                "apiBaseUrl", "https://models.invalid/v1",
                "apiKey", "credential-value"));
        when(modelClient.chatCompletionWithCredentials(
                any(), any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(new ChatCompletionResult(
                        "assistant",
                        objectMapper.writeValueAsString(withoutAssets(current)),
                        List.of(),
                        "stop",
                        10,
                        20));
        when(validation.validate(any(), any(Boolean.class))).thenReturn(List.of());
        when(persistence.saveForCurrentOrg(isA(OntologyAiProposalEntity.class)))
                .thenAnswer(invocation -> {
                    OntologyAiProposalEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        ReflectionTestUtils.setField(entity, "id", 501L);
                    }
                    return entity;
                });

        OntologyAiProposalService.ProposalView result = service.propose(
                "org-a",
                "user-a",
                41L,
                new OntologyAiProposalService.ProposalCommand(
                        "项目交付领域，包含项目、任务和负责人",
                        List.of(),
                        "DOMAIN_FIRST"));

        assertThat(result.id()).isEqualTo(501L);
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.baseRevision()).isEqualTo(3L);
        assertThat(result.candidate().dataSources())
                .containsExactlyInAnyOrderElementsOf(current.dataSources());
        assertThat(result.candidate().mappings())
                .containsExactlyInAnyOrderElementsOf(current.mappings());
        assertThat(result.diff().baseRevision()).isEqualTo(3L);
        verify(drafts, never()).saveDraft(any(), any(), any(), any(), any());
        verify(modelRouter).route("org-a", "ontology-modeling");
        verify(modelProviders).credentialsForProvider("org-a", "provider-a");
        verify(workspaces, never()).findForUpdateByIdAndOrgId(any(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> messages = ArgumentCaptor.forClass(List.class);
        verify(modelClient).chatCompletionWithCredentials(
                any(), messages.capture(), any(), any(Boolean.class), any(), any());
        String prompt = messages.getValue().toString();
        assertThat(prompt)
                .contains("credentials", "SQL", "scripts", "publishing", "write-back")
                .doesNotContain("server-secret", "private-row", "credential-value");
    }

    @Test
    void rejectsCrossTenantContextBeforeAnyModelCall() {
        assertThatThrownBy(() -> service.propose(
                "org-b",
                "user-b",
                41L,
                new OntologyAiProposalService.ProposalCommand(
                        "项目交付",
                        List.of(),
                        "DOMAIN_FIRST")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("AI_PROPOSAL_INVALID");

        verifyNoInteractions(workspaces, modelRouter, modelProviders, modelClient, persistence);
    }

    @Test
    void rejectsMalformedJsonVariantsAsFailedWithoutChangingDraft() throws Exception {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        String validJson = objectMapper.writeValueAsString(withoutAssets(current));
        List<String> invalidResponses = List.of(
                "explanation before JSON\n" + validJson,
                validJson + "\n{}",
                validJson.replaceFirst("\\{", "{\"unexpected\":true,"),
                validJson.replaceFirst(
                        "\"key\":\"project-delivery\"",
                        "\"key\":\"first\",\"key\":\"project-delivery\""),
                "{\"key\":\"missing-fields\"}",
                validJson.replaceFirst("\"ENTITY\"", "\"NOT_A_CONCEPT_TYPE\""),
                validJson.replaceFirst("\"queryable\":true", "\"queryable\":\"true\"")
        );
        AtomicLong ids = new AtomicLong(700L);
        stubWorkspaceAndRoute(current, ids);

        for (String invalid : invalidResponses) {
            when(modelClient.chatCompletionWithCredentials(
                    any(), any(), any(), any(Boolean.class), any(), any()))
                    .thenReturn(new ChatCompletionResult(
                            "assistant", invalid, List.of(), "stop", 10, 20));

            OntologyAiProposalService.ProposalView result = service.propose(
                    "org-a",
                    "user-a",
                    41L,
                    domainFirstCommand());

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.diagnosticCode()).isEqualTo("AI_PROPOSAL_INVALID");
            assertThat(result.candidate()).isNull();
        }

        verify(drafts, never()).saveDraft(any(), any(), any(), any(), any());
    }

    @Test
    void acceptsExactlyOneCompleteOuterJsonCodeFence() throws Exception {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        stubWorkspaceAndRoute(current, new AtomicLong(800L));
        String candidate = objectMapper.writeValueAsString(withoutAssets(current));
        when(modelClient.chatCompletionWithCredentials(
                any(), any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(new ChatCompletionResult(
                        "assistant", "```json\n" + candidate + "\n```", List.of(), "stop", 10, 20));
        OntologyAiProposalService.ProposalView result = service.propose(
                "org-a", "user-a", 41L, domainFirstCommand());

        assertThat(result.status()).isEqualTo("READY");
    }

    @Test
    void modelInfrastructureFailureProducesSanitizedFailedProposal() {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        OntologyWorkspaceEntity workspace = workspace(41L, 3L);
        when(workspaces.findByIdAndOrgId(41L, "org-a")).thenReturn(Optional.of(workspace));
        when(drafts.loadDraft("org-a", 41L, workspace)).thenReturn(current);
        when(modelRouter.route("org-a", "ontology-modeling"))
                .thenThrow(new IllegalStateException("provider leaked-secret"));
        AtomicLong ids = new AtomicLong(900L);
        when(persistence.saveForCurrentOrg(isA(OntologyAiProposalEntity.class)))
                .thenAnswer(invocation -> assignProposalId(invocation.getArgument(0), ids));

        OntologyAiProposalService.ProposalView result = service.propose(
                "org-a", "user-a", 41L, domainFirstCommand());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.diagnosticCode()).isEqualTo("AI_MODEL_UNAVAILABLE");
        assertThat(result.diagnosticMessage()).doesNotContain("leaked-secret");
        ArgumentCaptor<OntologyAiProposalEntity> saved =
                ArgumentCaptor.forClass(OntologyAiProposalEntity.class);
        verify(persistence, org.mockito.Mockito.atLeast(2)).saveForCurrentOrg(saved.capture());
        OntologyAiProposalEntity failed = saved.getAllValues().getLast();
        assertThat(failed.getPayloadJson()).isEqualTo("{}");
        assertThat(failed.getValidationJson()).doesNotContain("leaked-secret");
        verifyNoInteractions(modelProviders, modelClient);
    }

    @Test
    void truncatedOrOversizedModelResponseProducesStableFailedDiagnostic() throws Exception {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        stubWorkspaceAndRoute(current, new AtomicLong(1_000L));
        String candidate = objectMapper.writeValueAsString(withoutAssets(current));
        when(modelClient.chatCompletionWithCredentials(
                any(), any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(
                        new ChatCompletionResult(
                                "assistant", candidate, List.of(), "length", 10, 20),
                        new ChatCompletionResult(
                                "assistant", " ".repeat(OntologyAiProposalService.MAX_RESPONSE_BYTES + 1),
                                List.of(), "stop", 10, 20));

        OntologyAiProposalService.ProposalView truncated = service.propose(
                "org-a", "user-a", 41L, domainFirstCommand());
        OntologyAiProposalService.ProposalView oversized = service.propose(
                "org-a", "user-a", 41L, domainFirstCommand());

        assertThat(truncated.status()).isEqualTo("FAILED");
        assertThat(truncated.diagnosticMessage()).isEqualTo("AI_RESPONSE_TRUNCATED");
        assertThat(oversized.status()).isEqualTo("FAILED");
        assertThat(oversized.diagnosticMessage()).isEqualTo("AI_RESPONSE_TOO_LARGE");
    }

    @Test
    void enforcesConceptAndPropertyLimitsBeforeReadyState() throws Exception {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        stubWorkspaceAndRoute(current, new AtomicLong(1_100L));
        OntologyDocument.Concept template = current.concepts().getFirst();
        List<OntologyDocument.Concept> concepts = java.util.stream.IntStream.range(0, 101)
                .mapToObj(index -> new OntologyDocument.Concept(
                        "entity-" + index,
                        "业务对象 " + index,
                        "业务对象 " + index,
                        "",
                        template.conceptType(),
                        null,
                        index,
                        index,
                        true,
                        true,
                        List.of(new OntologyDocument.Property(
                                "name",
                                "名称",
                                "",
                                OntologyDocument.DataType.TEXT,
                                false,
                                false,
                                false,
                                true,
                                List.of()))))
                .toList();
        OntologyDocument tooManyConcepts = new OntologyDocument(
                "generic-domain",
                "通用领域",
                "边界测试",
                concepts,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        when(modelClient.chatCompletionWithCredentials(
                any(), any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(new ChatCompletionResult(
                        "assistant",
                        objectMapper.writeValueAsString(tooManyConcepts),
                        List.of(),
                        "stop",
                        10,
                        20));
        OntologyAiProposalService.ProposalView result = service.propose(
                "org-a", "user-a", 41L, domainFirstCommand());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.diagnosticMessage()).isEqualTo("ONTOLOGY_DOCUMENT_LIMIT_EXCEEDED");
    }

    @Test
    void dataSourceFirstUsesOnlyWhitelistedMetadataAndPreservesServerAssets() throws Exception {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        stubWorkspaceAndRoute(current, new AtomicLong(1_200L));
        OntologyPhysicalObjectEntity object = new OntologyPhysicalObjectEntity(
                "org-a",
                41L,
                1L,
                "projects",
                "项目台账",
                "TABLE",
                "{\"apiKey\":\"catalog-secret\"}");
        ReflectionTestUtils.setField(object, "id", 71L);
        OntologyPhysicalFieldEntity field = new OntologyPhysicalFieldEntity(
                "org-a",
                41L,
                71L,
                "name",
                "项目名称",
                "TEXT",
                false,
                false,
                "{\"sample\":\"private-field-value\"}");
        ReflectionTestUtils.setField(field, "id", 72L);
        when(physicalObjects.findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                1L, 41L, "org-a")).thenReturn(List.of(object));
        when(physicalFields.findByPhysicalObjectIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                71L, 41L, "org-a")).thenReturn(List.of(field));

        OntologyDocument generated = new OntologyDocument(
                current.key(),
                current.name(),
                current.description(),
                current.concepts(),
                current.relations(),
                current.metrics(),
                current.actions(),
                List.of(new OntologyDocument.DataSource(
                        1L,
                        "delivery-source",
                        "交付数据",
                        OntologyDocument.SourceType.INLINE_SAMPLE,
                        null)),
                List.of(new OntologyDocument.Mapping(
                        "PROPERTY",
                        "project.name",
                        1L,
                        "projects",
                        "name",
                        null,
                        "DIRECT",
                        0.8,
                        "AI",
                        "PENDING")));
        when(modelClient.chatCompletionWithCredentials(
                any(), any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(new ChatCompletionResult(
                        "assistant",
                        objectMapper.writeValueAsString(generated),
                        List.of(),
                        "stop",
                        10,
                        20));
        when(validation.validate(any(), any(Boolean.class))).thenReturn(List.of());

        OntologyAiProposalService.ProposalView result = service.propose(
                "org-a",
                "user-a",
                41L,
                new OntologyAiProposalService.ProposalCommand(
                        "根据项目台账生成业务语义",
                        List.of(new OntologyAiProposalService.SourceSelection(
                                1L, "projects", List.of("name"))),
                        "DATA_SOURCE_FIRST"));

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.candidate().dataSources())
                .containsExactlyInAnyOrderElementsOf(current.dataSources());
        assertThat(result.candidate().mappings())
                .containsExactlyInAnyOrderElementsOf(current.mappings());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> messages = ArgumentCaptor.forClass(List.class);
        verify(modelClient).chatCompletionWithCredentials(
                any(), messages.capture(), any(), any(Boolean.class), any(), any());
        String prompt = messages.getValue().toString();
        assertThat(prompt)
                .contains("delivery-source", "projects", "项目台账", "name", "项目名称", "TEXT")
                .doesNotContain(
                        "server-secret",
                        "private-row",
                        "catalog-secret",
                        "private-field-value",
                        "credential-value");
    }

    @Test
    void rejectsUnknownDataSourceSelectionBeforeModelInvocation() {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        OntologyWorkspaceEntity workspace = workspace(41L, 3L);
        when(workspaces.findByIdAndOrgId(41L, "org-a")).thenReturn(Optional.of(workspace));
        when(drafts.loadDraft("org-a", 41L, workspace)).thenReturn(current);

        assertThatThrownBy(() -> service.propose(
                "org-a",
                "user-a",
                41L,
                new OntologyAiProposalService.ProposalCommand(
                        "根据未知来源生成",
                        List.of(new OntologyAiProposalService.SourceSelection(
                                999L, "unknown-object", List.of("unknown-field"))),
                        "DATA_SOURCE_FIRST")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI_PROPOSAL_INVALID");

        verifyNoInteractions(modelRouter, modelProviders, modelClient, persistence);
    }

    @Test
    void rejectsModelMappingOutsideSelectedObjectAndFieldWhitelist() throws Exception {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        stubWorkspaceAndRoute(current, new AtomicLong(1_300L));
        OntologyPhysicalObjectEntity object = new OntologyPhysicalObjectEntity(
                "org-a", 41L, 1L, "projects", "项目台账", "TABLE", "{}");
        ReflectionTestUtils.setField(object, "id", 81L);
        OntologyPhysicalFieldEntity field = new OntologyPhysicalFieldEntity(
                "org-a", 41L, 81L, "name", "项目名称", "TEXT", false, false, "{}");
        ReflectionTestUtils.setField(field, "id", 82L);
        when(physicalObjects.findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                1L, 41L, "org-a")).thenReturn(List.of(object));
        when(physicalFields.findByPhysicalObjectIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                81L, 41L, "org-a")).thenReturn(List.of(field));
        OntologyDocument generated = new OntologyDocument(
                current.key(), current.name(), current.description(), current.concepts(),
                current.relations(), current.metrics(), current.actions(),
                List.of(),
                List.of(new OntologyDocument.Mapping(
                        "PROPERTY", "project.name", 1L, "projects", "unselected-field", null,
                        "DIRECT", 0.8, "AI", "PENDING")));
        when(modelClient.chatCompletionWithCredentials(
                any(), any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(new ChatCompletionResult(
                        "assistant", objectMapper.writeValueAsString(generated),
                        List.of(), "stop", 10, 20));

        OntologyAiProposalService.ProposalView result = service.propose(
                "org-a",
                "user-a",
                41L,
                new OntologyAiProposalService.ProposalCommand(
                        "根据已选字段生成",
                        List.of(new OntologyAiProposalService.SourceSelection(
                                1L, "projects", List.of("name"))),
                        "DATA_SOURCE_FIRST"));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.diagnosticMessage()).isEqualTo("AI_MAPPING_REFERENCE_NOT_ALLOWED");
        verify(drafts, never()).saveDraft(any(), any(), any(), any(), any());
    }

    @Test
    void atomicallyAppliesReadyProposalOnceAfterWorkspaceAndProposalLocks() throws Exception {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        OntologyAiProposalEntity proposal = readyProposal(1_401L, current, 3L);
        OntologyWorkspaceEntity workspace = workspace(41L, 3L);
        when(proposals.findWorkspaceIdByIdAndOrgId(1_401L, "org-a"))
                .thenReturn(Optional.of(41L));
        when(workspaces.findForUpdateByIdAndOrgId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));
        when(proposals.findForUpdateByIdAndOrgId(1_401L, "org-a"))
                .thenReturn(Optional.of(proposal));
        when(drafts.loadDraft("org-a", 41L, workspace)).thenReturn(current);
        when(validation.validate(any(), any(Boolean.class))).thenReturn(List.of());
        when(drafts.saveDraft("org-a", "user-a", 41L, 3L, current))
                .thenAnswer(invocation -> {
                    workspace.applyDraftMetadata(
                            current.key(), current.name(), current.description(), "user-a");
                    return workspace;
                });
        when(persistence.saveForCurrentOrg(proposal)).thenReturn(proposal);

        OntologyAiProposalService.ProposalView applied = service.apply(
                "org-a", "user-a", 1_401L, 3L);

        assertThat(applied.status()).isEqualTo("APPLIED");
        assertThat(applied.candidate()).isEqualTo(current);
        assertThat(proposal.getAppliedBy()).isEqualTo("user-a");
        assertThat(proposal.getAppliedAt()).isNotNull();
        verify(drafts).saveDraft("org-a", "user-a", 41L, 3L, current);
        verify(persistence).saveForCurrentOrg(proposal);

        InOrder order = org.mockito.Mockito.inOrder(proposals, workspaces, drafts, persistence);
        order.verify(proposals).findWorkspaceIdByIdAndOrgId(1_401L, "org-a");
        order.verify(workspaces).findForUpdateByIdAndOrgId(41L, "org-a");
        order.verify(proposals).findForUpdateByIdAndOrgId(1_401L, "org-a");
        order.verify(drafts).loadDraft("org-a", 41L, workspace);
        order.verify(drafts).saveDraft("org-a", "user-a", 41L, 3L, current);
        order.verify(persistence).saveForCurrentOrg(proposal);
    }

    @Test
    void rejectsApplyRevisionConflictWithoutChangingReadyProposal() throws Exception {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        OntologyAiProposalEntity proposal = readyProposal(1_402L, current, 3L);
        OntologyWorkspaceEntity workspace = workspace(41L, 3L);
        when(proposals.findWorkspaceIdByIdAndOrgId(1_402L, "org-a"))
                .thenReturn(Optional.of(41L));
        when(workspaces.findForUpdateByIdAndOrgId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));
        when(proposals.findForUpdateByIdAndOrgId(1_402L, "org-a"))
                .thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> service.apply("org-a", "user-a", 1_402L, 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("ONTOLOGY_REVISION_CONFLICT");

        assertThat(proposal.getStatus()).isEqualTo("READY");
        verify(drafts, never()).saveDraft(any(), any(), any(), any(), any());
        verifyNoInteractions(persistence);
    }

    @Test
    void rejectsTamperedOrAlreadyAppliedProposalWithoutDraftMutation() throws Exception {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        OntologyWorkspaceEntity workspace = workspace(41L, 3L);
        OntologyAiProposalEntity tampered = readyProposal(1_403L, current, 3L);
        ReflectionTestUtils.setField(
                tampered,
                "payloadJson",
                objectMapper.writeValueAsString(withoutAssets(current)));
        when(proposals.findWorkspaceIdByIdAndOrgId(1_403L, "org-a"))
                .thenReturn(Optional.of(41L));
        when(workspaces.findForUpdateByIdAndOrgId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));
        when(proposals.findForUpdateByIdAndOrgId(1_403L, "org-a"))
                .thenReturn(Optional.of(tampered));

        assertThatThrownBy(() -> service.apply("org-a", "user-a", 1_403L, 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI_PROPOSAL_INVALID");
        assertThat(tampered.getStatus()).isEqualTo("READY");

        OntologyAiProposalEntity applied = readyProposal(1_404L, current, 3L);
        applied.markApplied("user-a");
        when(proposals.findWorkspaceIdByIdAndOrgId(1_404L, "org-a"))
                .thenReturn(Optional.of(41L));
        when(proposals.findForUpdateByIdAndOrgId(1_404L, "org-a"))
                .thenReturn(Optional.of(applied));
        assertThatThrownBy(() -> service.apply("org-a", "user-a", 1_404L, 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI_PROPOSAL_INVALID");

        verify(drafts, never()).saveDraft(any(), any(), any(), any(), any());
        verifyNoInteractions(persistence);
    }

    @Test
    void rejectsCrossTenantApplyBeforeProposalLookup() {
        assertThatThrownBy(() -> service.apply("org-b", "user-b", 1_405L, 3L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("AI_PROPOSAL_INVALID");

        verifyNoInteractions(proposals, workspaces, drafts, validation, persistence);
    }

    @Test
    void rejectsEmptyEnumValuesBeforeCollaboratingValidator() throws Exception {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        stubWorkspaceAndRoute(current, new AtomicLong(1_500L));
        OntologyDocument.Concept task = current.concepts().stream()
                .filter(concept -> "task".equals(concept.key()))
                .findFirst().orElseThrow();
        OntologyDocument.Property status = task.properties().getFirst();
        OntologyDocument.Concept invalidTask = new OntologyDocument.Concept(
                task.key(), task.name(), task.pluralName(), task.description(), task.conceptType(),
                task.displayPropertyKey(), task.positionX(), task.positionY(), task.queryable(),
                task.enabled(), List.of(new OntologyDocument.Property(
                        status.key(), status.name(), status.description(), status.dataType(),
                        status.required(), status.multiple(), status.sensitive(), status.queryable(),
                        List.of())));
        List<OntologyDocument.Concept> concepts = current.concepts().stream()
                .map(concept -> "task".equals(concept.key()) ? invalidTask : concept)
                .toList();
        OntologyDocument generated = new OntologyDocument(
                current.key(), current.name(), current.description(), concepts,
                current.relations(), current.metrics(), current.actions(), List.of(), List.of());
        when(modelClient.chatCompletionWithCredentials(
                any(), any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(new ChatCompletionResult(
                        "assistant", objectMapper.writeValueAsString(generated),
                        List.of(), "stop", 10, 20));
        OntologyAiProposalService.ProposalView result = service.propose(
                "org-a", "user-a", 41L, domainFirstCommand());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.diagnosticMessage()).isEqualTo("ONTOLOGY_ENUM_VALUES_INVALID");
    }

    @Test
    void computesChangedDiffByStableBusinessKey() throws Exception {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        stubWorkspaceAndRoute(current, new AtomicLong(1_600L));
        List<OntologyDocument.Concept> changedConcepts = current.concepts().stream()
                .map(concept -> {
                    if (!"project".equals(concept.key())) {
                        return concept;
                    }
                    List<OntologyDocument.Property> changedProperties = concept.properties().stream()
                            .map(property -> "name".equals(property.key())
                                    ? new OntologyDocument.Property(
                                            property.key(), "项目名称", property.description(),
                                            property.dataType(), property.required(), property.multiple(),
                                            property.sensitive(), property.queryable(), property.enumValues())
                                    : property)
                            .toList();
                    return new OntologyDocument.Concept(
                            concept.key(), concept.name(), concept.pluralName(), concept.description(),
                            concept.conceptType(), concept.displayPropertyKey(), concept.positionX(),
                            concept.positionY(), concept.queryable(), concept.enabled(), changedProperties);
                })
                .toList();
        OntologyDocument generated = new OntologyDocument(
                current.key(), current.name(), current.description(), changedConcepts,
                current.relations(), current.metrics(), current.actions(), List.of(), List.of());
        when(modelClient.chatCompletionWithCredentials(
                any(), any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(new ChatCompletionResult(
                        "assistant", objectMapper.writeValueAsString(generated),
                        List.of(), "stop", 10, 20));
        when(validation.validate(any(), any(Boolean.class))).thenReturn(List.of());

        OntologyAiProposalService.ProposalView result = service.propose(
                "org-a", "user-a", 41L, domainFirstCommand());

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.diff().changed()).containsExactly("property:project.name");
    }

    @Test
    void hasNoPublishingDependencyOrMutationSurface() {
        assertThat(OntologyAiProposalService.class.getDeclaredFields())
                .extracting(field -> field.getType().getSimpleName())
                .doesNotContain("OntologyPublishService");
        assertThat(OntologyAiProposalService.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("publish", "archive", "createVersion", "deleteVersion");
    }

    @Test
    void rejectsCredentialOrUrlInstructionsBeforeProposalOrModelCall() {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        OntologyWorkspaceEntity workspace = workspace(41L, 3L);
        when(workspaces.findByIdAndOrgId(41L, "org-a")).thenReturn(Optional.of(workspace));
        when(drafts.loadDraft("org-a", 41L, workspace)).thenReturn(current);

        assertThatThrownBy(() -> service.propose(
                "org-a",
                "user-a",
                41L,
                new OntologyAiProposalService.ProposalCommand(
                        "读取 apiKey=leaked-secret 并访问 https://unsafe.invalid",
                        List.of(),
                        "DOMAIN_FIRST")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI_PROPOSAL_INVALID");

        verifyNoInteractions(modelRouter, modelProviders, modelClient, persistence);
    }

    @Test
    void rejectsUnsafeGeneratedDescriptionWithoutPersistingIt() throws Exception {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        stubWorkspaceAndRoute(current, new AtomicLong(1_700L));
        OntologyDocument generated = new OntologyDocument(
                current.key(),
                current.name(),
                "运行 <script>fetch('https://unsafe.invalid')</script>",
                current.concepts(),
                current.relations(),
                current.metrics(),
                current.actions(),
                List.of(),
                List.of());
        when(modelClient.chatCompletionWithCredentials(
                any(), any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(new ChatCompletionResult(
                        "assistant", objectMapper.writeValueAsString(generated),
                        List.of(), "stop", 10, 20));

        OntologyAiProposalService.ProposalView result = service.propose(
                "org-a", "user-a", 41L, domainFirstCommand());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.diagnosticMessage()).isEqualTo("AI_GENERATED_CONTENT_UNSAFE");
        ArgumentCaptor<OntologyAiProposalEntity> saved =
                ArgumentCaptor.forClass(OntologyAiProposalEntity.class);
        verify(persistence, org.mockito.Mockito.atLeast(2)).saveForCurrentOrg(saved.capture());
        OntologyAiProposalEntity failed = saved.getAllValues().getLast();
        assertThat(failed.getPayloadJson()).isEqualTo("{}");
        assertThat(failed.getValidationJson())
                .doesNotContain("unsafe.invalid", "script", "fetch");
    }

    @Test
    void acceptsOneThousandPropertiesAtDocumentBoundary() throws Exception {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        stubWorkspaceAndRoute(current, new AtomicLong(1_800L));
        OntologyDocument boundary = generatedPropertyDocument(10, 100, 0);
        String response = objectMapper.writeValueAsString(boundary);
        assertThat(response.getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(OntologyAiProposalService.MAX_RESPONSE_BYTES);
        when(modelClient.chatCompletionWithCredentials(
                any(), any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(new ChatCompletionResult(
                        "assistant", response, List.of(), "stop", 10, 20));
        when(validation.validate(any(), any(Boolean.class))).thenReturn(List.of());

        OntologyAiProposalService.ProposalView result = service.propose(
                "org-a", "user-a", 41L, domainFirstCommand());

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.candidate().concepts())
                .flatExtracting(OntologyDocument.Concept::properties)
                .hasSize(1_000);
    }

    @Test
    void rejectsPerConceptAndTotalPropertyOverflow() throws Exception {
        OntologyDocument current = withSecretConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        stubWorkspaceAndRoute(current, new AtomicLong(1_900L));
        OntologyDocument perConceptOverflow = generatedPropertyDocument(1, 101, 0);
        OntologyDocument totalOverflow = generatedPropertyDocument(10, 100, 1);
        when(modelClient.chatCompletionWithCredentials(
                any(), any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(
                        new ChatCompletionResult(
                                "assistant", objectMapper.writeValueAsString(perConceptOverflow),
                                List.of(), "stop", 10, 20),
                        new ChatCompletionResult(
                                "assistant", objectMapper.writeValueAsString(totalOverflow),
                                List.of(), "stop", 10, 20));

        OntologyAiProposalService.ProposalView first = service.propose(
                "org-a", "user-a", 41L, domainFirstCommand());
        OntologyAiProposalService.ProposalView second = service.propose(
                "org-a", "user-a", 41L, domainFirstCommand());

        assertThat(first.status()).isEqualTo("FAILED");
        assertThat(first.diagnosticMessage()).isEqualTo("ONTOLOGY_DOCUMENT_LIMIT_EXCEEDED");
        assertThat(second.status()).isEqualTo("FAILED");
        assertThat(second.diagnosticMessage()).isEqualTo("ONTOLOGY_DOCUMENT_LIMIT_EXCEEDED");
    }

    private OntologyWorkspaceEntity workspace(Long id, Long revision) {
        OntologyWorkspaceEntity workspace = new OntologyWorkspaceEntity(
                "org-a", "project-delivery", "项目交付", "通用样例", "creator");
        ReflectionTestUtils.setField(workspace, "id", id);
        ReflectionTestUtils.setField(workspace, "draftRevision", revision);
        return workspace;
    }

    private OntologyAiProposalService.ProposalCommand domainFirstCommand() {
        return new OntologyAiProposalService.ProposalCommand(
                "项目交付领域，包含项目、任务和负责人",
                List.of(),
                "DOMAIN_FIRST");
    }

    private void stubWorkspaceAndRoute(OntologyDocument current, AtomicLong ids) {
        OntologyWorkspaceEntity workspace = workspace(41L, 3L);
        when(workspaces.findByIdAndOrgId(41L, "org-a")).thenReturn(Optional.of(workspace));
        when(drafts.loadDraft("org-a", 41L, workspace)).thenReturn(current);
        when(modelRouter.route("org-a", "ontology-modeling")).thenReturn(Map.of(
                "provider", "provider-a",
                "modelName", "model-a"));
        when(modelProviders.credentialsForProvider("org-a", "provider-a")).thenReturn(Map.of(
                "enabled", "true",
                "apiBaseUrl", "https://models.invalid/v1",
                "apiKey", "credential-value"));
        when(persistence.saveForCurrentOrg(isA(OntologyAiProposalEntity.class)))
                .thenAnswer(invocation -> assignProposalId(invocation.getArgument(0), ids));
    }

    private OntologyAiProposalEntity assignProposalId(
            OntologyAiProposalEntity entity,
            AtomicLong ids) {
        if (entity.getId() == null) {
            ReflectionTestUtils.setField(entity, "id", ids.incrementAndGet());
        }
        return entity;
    }

    private OntologyAiProposalEntity readyProposal(
            Long id,
            OntologyDocument candidate,
            long baseRevision) throws Exception {
        String payload = objectMapper.writeValueAsString(candidate);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(StandardCharsets.UTF_8)));
        OntologyAiProposalService.ProposalDiff diff =
                new OntologyAiProposalService.ProposalDiff(
                        baseRevision, hash, List.of(), List.of(), List.of());
        OntologyAiProposalEntity proposal = new OntologyAiProposalEntity(
                "org-a",
                41L,
                "DOMAIN_FIRST",
                "项目交付",
                "{}",
                objectMapper.writeValueAsString(diff),
                "[]",
                "user-a");
        ReflectionTestUtils.setField(proposal, "id", id);
        proposal.markReady(payload, objectMapper.writeValueAsString(diff), "[]");
        return proposal;
    }

    private OntologyDocument withSecretConfig(OntologyDocument document) {
        OntologyDocument.DataSource source = document.dataSources().getFirst();
        return new OntologyDocument(
                document.key(),
                document.name(),
                document.description(),
                document.concepts(),
                document.relations(),
                document.metrics(),
                document.actions(),
                List.of(new OntologyDocument.DataSource(
                        source.id(),
                        source.key(),
                        source.name(),
                        source.type(),
                        "{\"apiKey\":\"server-secret\",\"records\":[{\"name\":\"private-row\"}]}")),
                document.mappings());
    }

    private OntologyDocument withoutAssets(OntologyDocument document) {
        return new OntologyDocument(
                document.key(),
                document.name(),
                document.description(),
                document.concepts(),
                document.relations(),
                document.metrics(),
                document.actions(),
                List.of(),
                List.of());
    }

    private OntologyDocument generatedPropertyDocument(
            int fullConceptCount,
            int propertiesPerFullConcept,
            int extraProperties) {
        List<OntologyDocument.Concept> concepts = new java.util.ArrayList<>();
        for (int conceptIndex = 0; conceptIndex < fullConceptCount; conceptIndex++) {
            concepts.add(generatedConcept(
                    "entity-" + conceptIndex,
                    propertiesPerFullConcept));
        }
        if (extraProperties > 0) {
            concepts.add(generatedConcept("extra-entity", extraProperties));
        }
        return new OntologyDocument(
                "generic-domain",
                "通用领域",
                "属性边界",
                List.copyOf(concepts),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private OntologyDocument.Concept generatedConcept(String key, int propertyCount) {
        List<OntologyDocument.Property> properties = java.util.stream.IntStream
                .range(0, propertyCount)
                .mapToObj(index -> new OntologyDocument.Property(
                        "p" + index,
                        "P" + index,
                        "",
                        OntologyDocument.DataType.TEXT,
                        false,
                        false,
                        false,
                        true,
                        List.of()))
                .toList();
        return new OntologyDocument.Concept(
                key,
                "E",
                "E",
                "",
                OntologyDocument.ConceptType.ENTITY,
                propertyCount == 0 ? null : "p0",
                0,
                0,
                true,
                true,
                properties);
    }
}
