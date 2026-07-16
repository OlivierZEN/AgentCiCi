package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ontology.adapter.InlineSampleOntologyAdapter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter;
import com.codehouse.ciciassistant.ontology.domain.OntologyQueryAuditEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

class SemanticQueryServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OntologyWorkspaceRepository workspaces = mock(OntologyWorkspaceRepository.class);
    private final OntologyVersionRepository versions = mock(OntologyVersionRepository.class);
    private final OntologyTenantPersistence persistence = mock(OntologyTenantPersistence.class);

    @BeforeEach
    void setUpTenant() {
        TenantContext.setOrgId("org-a");
        TenantContext.setUserId("user-a");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void routesPublishedInlineQueryAndReturnsEvidence() throws Exception {
        OntologyDocument snapshot = projectDeliverySnapshot();
        stubPublishedSnapshot(snapshot, 1);
        when(persistence.saveForCurrentOrg(any(OntologyQueryAuditEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SemanticQueryService service = serviceWith(
                new InlineSampleOntologyAdapter(objectMapper));

        SemanticQueryService.QueryResult result = service.execute(
                "org-a",
                "user-a",
                new SemanticQueryService.SemanticQuery(
                        "project-delivery",
                        1,
                        "task",
                        List.of("name", "status"),
                        List.of(new SemanticQueryService.Filter(
                                "status", "EQ", "IN_PROGRESS")),
                        List.of(),
                        50));

        assertThat(result.rows())
                .extracting(row -> row.get("name"))
                .containsExactly("语义平台设计");
        assertThat(result.evidence().sourceType()).isEqualTo("INLINE_SAMPLE");
        assertThat(result.evidence().ontologyVersion()).isEqualTo(1);
        assertThat(result.evidence().dataSourceKey()).isEqualTo("delivery-source");
        assertThat(result.elapsedMs()).isNotNegative();

        ArgumentCaptor<OntologyQueryAuditEntity> audit =
                ArgumentCaptor.forClass(OntologyQueryAuditEntity.class);
        org.mockito.Mockito.verify(persistence).saveForCurrentOrg(audit.capture());
        assertThat(audit.getValue().getStatus()).isEqualTo("SUCCEEDED");
        assertThat(audit.getValue().getResultCount()).isEqualTo(1);
        assertThat(audit.getValue().isSensitiveValuesRedacted()).isTrue();
        assertThat(audit.getValue().getQueryJson())
                .contains("REDACTED")
                .doesNotContain("IN_PROGRESS");
    }

    @Test
    void rejectsLimitAboveTwoHundredBeforeAdapterCall() {
        OntologyDataSourceAdapter adapter = mock(OntologyDataSourceAdapter.class);
        SemanticQueryService service = serviceWith(adapter);

        assertThatThrownBy(() -> service.explain(
                "org-a",
                "user-a",
                new SemanticQueryService.SemanticQuery(
                        "project-delivery",
                        1,
                        "task",
                        List.of("name"),
                        List.of(),
                        List.of(),
                        201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("QUERY_BUDGET_EXCEEDED");

        verifyNoInteractions(adapter, workspaces, versions, persistence);
    }

    @Test
    void rejectsDraftOnlyVersionInsteadOfReadingMutableDraftState() {
        OntologyWorkspaceEntity workspace = workspace(41L, 1);
        when(workspaces.findByOrgIdAndKey("org-a", "project-delivery"))
                .thenReturn(Optional.of(workspace));
        when(versions.findByWorkspaceIdAndOrgIdAndVersionNo(41L, "org-a", 2))
                .thenReturn(Optional.empty());
        OntologyDataSourceAdapter adapter = mock(OntologyDataSourceAdapter.class);
        SemanticQueryService service = serviceWith(adapter);

        assertThatThrownBy(() -> service.explain(
                "org-a",
                "user-a",
                new SemanticQueryService.SemanticQuery(
                        "project-delivery", 2, "task", List.of("name"),
                        List.of(), List.of(), 50)))
                .hasMessageContaining("ONTOLOGY_VERSION_NOT_PUBLISHED");

        verifyNoInteractions(adapter, persistence);
    }

    @Test
    void rejectsUnknownSensitiveAndUnmappedFieldsFromPublishedSnapshot() throws Exception {
        stubPublishedSnapshot(projectDeliverySnapshot(), 1);
        SemanticQueryService service = serviceWith(
                new InlineSampleOntologyAdapter(objectMapper));

        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", querySelecting("unknown")))
                .hasMessageContaining("QUERY_FIELD_UNKNOWN");
        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", querySelecting("private-note")))
                .hasMessageContaining("QUERY_FIELD_SENSITIVE");
        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", querySelecting("owner")))
                .hasMessageContaining("QUERY_FIELD_UNMAPPED");

        verifyNoInteractions(persistence);
    }

    @Test
    void rejectsPlansThatSpanMoreThanOneSource() throws Exception {
        stubPublishedSnapshot(crossSourceSnapshot(), 1);
        OntologyDataSourceAdapter adapter = mock(OntologyDataSourceAdapter.class);
        SemanticQueryService service = serviceWith(adapter);

        assertThatThrownBy(() -> service.explain(
                "org-a",
                "user-a",
                new SemanticQueryService.SemanticQuery(
                        "project-delivery", 1, "task", List.of("name", "status"),
                        List.of(), List.of(), 50)))
                .hasMessageContaining("CROSS_SOURCE_QUERY_NOT_SUPPORTED");

        verifyNoInteractions(adapter, persistence);
    }

    @Test
    void rejectsSelectionsBeyondOneRelationHop() throws Exception {
        stubPublishedSnapshot(projectDeliverySnapshot(), 1);
        SemanticQueryService service = serviceWith(
                new InlineSampleOntologyAdapter(objectMapper));

        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", querySelecting("project.owner.name")))
                .hasMessageContaining("RELATION_HOP_LIMIT_EXCEEDED");
    }

    @Test
    void includesOneHopRelationMappingsInTheCrossSourceBudget() throws Exception {
        stubPublishedSnapshot(oneHopCrossSourceSnapshot(), 1);
        SemanticQueryService service = serviceWith(
                new InlineSampleOntologyAdapter(objectMapper));

        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", querySelecting("owner-link.name")))
                .hasMessageContaining("CROSS_SOURCE_QUERY_NOT_SUPPORTED");
    }

    @Test
    void requiresTheCurrentTenantAndUserBeforeResolvingSnapshots() {
        SemanticQueryService service = serviceWith(mock(OntologyDataSourceAdapter.class));

        assertThatThrownBy(() -> service.explain(
                "org-b", "user-a", querySelecting("name")))
                .hasMessageContaining("ONTOLOGY_QUERY_CONTEXT_MISMATCH");
        assertThatThrownBy(() -> service.explain(
                "org-a", "user-b", querySelecting("name")))
                .hasMessageContaining("ONTOLOGY_QUERY_CONTEXT_MISMATCH");

        verifyNoInteractions(workspaces, versions, persistence);
    }

    @Test
    void rejectsNullFilterEntriesAndBlankCurrentUserWithStableDiagnostics() {
        SemanticQueryService service = serviceWith(mock(OntologyDataSourceAdapter.class));
        SemanticQueryService.SemanticQuery nullFilter = new SemanticQueryService.SemanticQuery(
                "project-delivery",
                1,
                "task",
                List.of("name"),
                Collections.singletonList(null),
                List.of(),
                50);

        assertThatThrownBy(() -> service.explain("org-a", "user-a", nullFilter))
                .hasMessage("QUERY_FILTER_REQUIRED");
        assertThatThrownBy(() -> service.explain(
                "org-a", null, querySelecting("name")))
                .hasMessage("ONTOLOGY_QUERY_CONTEXT_MISMATCH");
    }

    @Test
    void persistsFailedExecutionAuditOutsideARollbackOnlyOuterTransaction() throws Exception {
        stubPublishedSnapshot(projectDeliverySnapshot(), 1);
        OntologyDataSourceAdapter adapter = mock(OntologyDataSourceAdapter.class);
        when(adapter.supports(any())).thenReturn(true);
        when(adapter.executeRead(any(), any(), any()))
                .thenThrow(new IllegalStateException("CONNECTOR_READ_FAILED"));
        when(persistence.saveForCurrentOrg(any(OntologyQueryAuditEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SemanticQueryService service = serviceWith(adapter);

        assertThatThrownBy(() -> service.execute(
                "org-a", "user-a", querySelecting("name")))
                .hasMessage("CONNECTOR_READ_FAILED");

        ArgumentCaptor<OntologyQueryAuditEntity> audit =
                ArgumentCaptor.forClass(OntologyQueryAuditEntity.class);
        org.mockito.Mockito.verify(persistence).saveForCurrentOrg(audit.capture());
        assertThat(audit.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(audit.getValue().getErrorCode()).isEqualTo("CONNECTOR_READ_FAILED");
        assertThat(SemanticQueryService.class
                .getMethod(
                        "execute",
                        String.class,
                        String.class,
                        SemanticQueryService.SemanticQuery.class)
                .isAnnotationPresent(Transactional.class))
                .as("failure audit must commit before execute rethrows")
                .isFalse();
    }

    private SemanticQueryService serviceWith(OntologyDataSourceAdapter adapter) {
        return new SemanticQueryService(
                workspaces,
                versions,
                persistence,
                List.of(adapter),
                objectMapper);
    }

    private SemanticQueryService.SemanticQuery querySelecting(String field) {
        return new SemanticQueryService.SemanticQuery(
                "project-delivery", 1, "task", List.of(field),
                List.of(), List.of(), 50);
    }

    private void stubPublishedSnapshot(OntologyDocument document, int versionNo) throws Exception {
        OntologyWorkspaceEntity workspace = workspace(41L, versionNo);
        OntologyVersionEntity version = new OntologyVersionEntity(
                "org-a",
                41L,
                versionNo,
                1L,
                "hash",
                objectMapper.writeValueAsString(document),
                "{}",
                "type Query { task: Task }",
                "{}",
                "[]",
                "publisher");
        ReflectionTestUtils.setField(version, "id", 91L);
        when(workspaces.findByOrgIdAndKey("org-a", "project-delivery"))
                .thenReturn(Optional.of(workspace));
        when(versions.findByWorkspaceIdAndOrgIdAndVersionNo(
                41L, "org-a", versionNo))
                .thenReturn(Optional.of(version));
    }

    private OntologyWorkspaceEntity workspace(Long id, Integer publishedVersion) {
        OntologyWorkspaceEntity workspace = new OntologyWorkspaceEntity(
                "org-a", "project-delivery", "项目交付", "", "creator");
        ReflectionTestUtils.setField(workspace, "id", id);
        ReflectionTestUtils.setField(workspace, "publishedVersion", publishedVersion);
        return workspace;
    }

    private OntologyDocument projectDeliverySnapshot() {
        OntologyDocument.DataSource source = new OntologyDocument.DataSource(
                11L,
                "delivery-source",
                "交付数据",
                OntologyDocument.SourceType.INLINE_SAMPLE,
                """
                        {"tasks":[
                          {"name":"语义平台设计","status":"IN_PROGRESS","private_note":"secret"},
                          {"name":"发布验收","status":"DONE","private_note":"secret-2"}
                        ]}
                        """);
        OntologyDocument.Concept task = new OntologyDocument.Concept(
                "task",
                "任务",
                "任务",
                "",
                OntologyDocument.ConceptType.EVENT,
                "name",
                0,
                0,
                true,
                true,
                List.of(
                        property("name", false, true),
                        property("status", false, true),
                        property("owner", false, true),
                        property("private-note", true, false)));
        return new OntologyDocument(
                "project-delivery",
                "项目交付",
                "",
                List.of(task),
                List.of(),
                List.of(),
                List.of(),
                List.of(source),
                List.of(
                        mapping("CONCEPT", "task", 11L, "tasks", null),
                        mapping("PROPERTY", "task.name", 11L, "tasks", "name"),
                        mapping("PROPERTY", "task.status", 11L, "tasks", "status")));
    }

    private OntologyDocument crossSourceSnapshot() {
        OntologyDocument base = projectDeliverySnapshot();
        OntologyDocument.DataSource secondSource = new OntologyDocument.DataSource(
                12L,
                "status-source",
                "状态数据",
                OntologyDocument.SourceType.INLINE_SAMPLE,
                "{\"tasks\":[]}");
        return new OntologyDocument(
                base.key(),
                base.name(),
                base.description(),
                base.concepts(),
                base.relations(),
                base.metrics(),
                base.actions(),
                List.of(base.dataSources().get(0), secondSource),
                List.of(
                        mapping("CONCEPT", "task", 11L, "tasks", null),
                        mapping("PROPERTY", "task.name", 11L, "tasks", "name"),
                        mapping("PROPERTY", "task.status", 12L, "tasks", "status")));
    }

    private OntologyDocument oneHopCrossSourceSnapshot() {
        OntologyDocument base = projectDeliverySnapshot();
        OntologyDocument.Concept owner = new OntologyDocument.Concept(
                "owner",
                "负责人",
                "负责人",
                "",
                OntologyDocument.ConceptType.ENTITY,
                "name",
                0,
                0,
                true,
                true,
                List.of(property("name", false, true)));
        OntologyDocument.Relation ownerLink = new OntologyDocument.Relation(
                "owner-link",
                "负责人",
                "",
                "task",
                "owner",
                OntologyDocument.Cardinality.MANY_TO_ONE,
                "负责人",
                "任务",
                true,
                true);
        OntologyDocument.DataSource relationSource = new OntologyDocument.DataSource(
                12L,
                "relation-source",
                "关系数据",
                OntologyDocument.SourceType.INLINE_SAMPLE,
                "{\"tasks\":[]}");
        OntologyDocument.Mapping relationMapping = new OntologyDocument.Mapping(
                "RELATION",
                "owner-link",
                12L,
                "tasks",
                "owner_id",
                "id",
                "DIRECT",
                1,
                "MANUAL",
                "VALID");
        return new OntologyDocument(
                base.key(),
                base.name(),
                base.description(),
                List.of(base.concepts().get(0), owner),
                List.of(ownerLink),
                List.of(),
                List.of(),
                List.of(base.dataSources().get(0), relationSource),
                List.of(
                        mapping("CONCEPT", "task", 11L, "tasks", null),
                        mapping("PROPERTY", "task.name", 11L, "tasks", "name"),
                        mapping("PROPERTY", "task.status", 11L, "tasks", "status"),
                        mapping("CONCEPT", "owner", 11L, "tasks", null),
                        mapping("PROPERTY", "owner.name", 11L, "tasks", "owner_name"),
                        relationMapping));
    }

    private OntologyDocument.Property property(
            String key,
            boolean sensitive,
            boolean queryable) {
        return new OntologyDocument.Property(
                key,
                key,
                "",
                OntologyDocument.DataType.TEXT,
                false,
                false,
                sensitive,
                queryable,
                List.of());
    }

    private OntologyDocument.Mapping mapping(
            String targetType,
            String targetKey,
            Long dataSourceId,
            String objectKey,
            String fieldKey) {
        return new OntologyDocument.Mapping(
                targetType,
                targetKey,
                dataSourceId,
                objectKey,
                fieldKey,
                null,
                "DIRECT",
                1,
                "MANUAL",
                "VALID");
    }
}
