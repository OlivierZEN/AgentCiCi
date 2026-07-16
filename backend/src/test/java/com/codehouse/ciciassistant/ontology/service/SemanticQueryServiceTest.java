package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.ontology.adapter.CloudccOntologyAdapter;
import com.codehouse.ciciassistant.ontology.adapter.InlineSampleOntologyAdapter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalResult;
import com.codehouse.ciciassistant.ontology.domain.OntologyQueryAuditEntity;
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
import java.util.stream.IntStream;
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
    private final OntologyQueryAuditWriter auditWriter = mock(OntologyQueryAuditWriter.class);

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
        org.mockito.Mockito.verify(auditWriter).write(audit.capture());
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

        verifyNoInteractions(adapter, workspaces, versions, auditWriter);
    }

    @Test
    void rejectsGlobalFilterAndOrderComplexityBeforeSnapshotResolution() {
        OntologyDataSourceAdapter adapter = mock(OntologyDataSourceAdapter.class);
        SemanticQueryService service = serviceWith(adapter);
        List<SemanticQueryService.Filter> filters = IntStream.range(0, 21)
                .mapToObj(index -> new SemanticQueryService.Filter(
                        "name", "EQ", "value-" + index))
                .toList();
        List<SemanticQueryService.OrderBy> orderBy = IntStream.range(0, 6)
                .mapToObj(index -> new SemanticQueryService.OrderBy("name", "ASC"))
                .toList();

        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", new SemanticQueryService.SemanticQuery(
                        "project-delivery", 1, "task", List.of("name"),
                        filters, List.of(), 50)))
                .hasMessage("QUERY_FILTER_LIMIT_EXCEEDED");
        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", new SemanticQueryService.SemanticQuery(
                        "project-delivery", 1, "task", List.of("name"),
                        List.of(), orderBy, 50)))
                .hasMessage("QUERY_ORDER_LIMIT_EXCEEDED");

        verifyNoInteractions(adapter, workspaces, versions, auditWriter);
    }

    @Test
    void rejectsGlobalSelectAndRelationComplexityBeforeSnapshotResolution() {
        OntologyDataSourceAdapter adapter = mock(OntologyDataSourceAdapter.class);
        SemanticQueryService service = serviceWith(adapter);
        List<String> oversizedSelect = IntStream.range(0, 51)
                .mapToObj(index -> "field-" + index)
                .toList();
        List<String> oversizedRelationSelect = IntStream.range(0, 21)
                .mapToObj(index -> "tasks.field-" + index)
                .toList();
        List<String> oversizedRelationPlans = IntStream.range(0, 6)
                .mapToObj(index -> "relation-" + index + ".name")
                .toList();

        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", new SemanticQueryService.SemanticQuery(
                        "project-delivery", 1, "task", oversizedSelect,
                        List.of(), List.of(), 50)))
                .hasMessage("QUERY_SELECT_LIMIT_EXCEEDED");
        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", new SemanticQueryService.SemanticQuery(
                        "project-delivery", 1, "task", oversizedRelationSelect,
                        List.of(), List.of(), 50)))
                .hasMessage("QUERY_RELATION_SELECT_LIMIT_EXCEEDED");
        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", new SemanticQueryService.SemanticQuery(
                        "project-delivery", 1, "task", oversizedRelationPlans,
                        List.of(), List.of(), 50)))
                .hasMessage("QUERY_RELATION_PLAN_LIMIT_EXCEEDED");

        verifyNoInteractions(adapter, workspaces, versions, auditWriter);
    }

    @Test
    void rejectsOversizedOrNullInValuesBeforeSnapshotResolution() {
        OntologyDataSourceAdapter adapter = mock(OntologyDataSourceAdapter.class);
        SemanticQueryService service = serviceWith(adapter);
        List<Integer> oversized = IntStream.range(0, 101).boxed().toList();
        List<String> withNull = new java.util.ArrayList<>();
        withNull.add("ACTIVE");
        withNull.add(null);

        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", queryFiltering("IN", oversized)))
                .hasMessage("QUERY_IN_VALUE_LIMIT_EXCEEDED");
        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", queryFiltering("IN", withNull)))
                .hasMessage("QUERY_IN_VALUE_NULL");
        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", queryFiltering("IN", null)))
                .hasMessage("QUERY_IN_VALUES_REQUIRED");

        verifyNoInteractions(adapter, workspaces, versions, auditWriter);
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

        verifyNoInteractions(adapter, auditWriter);
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

        verifyNoInteractions(auditWriter);
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

        verifyNoInteractions(adapter, auditWriter);
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
    void rejectsAmbiguousValidMappingsInsteadOfChoosingTheFirst() throws Exception {
        OntologyDocument base = projectDeliverySnapshot();
        List<OntologyDocument.Mapping> mappings = new java.util.ArrayList<>(base.mappings());
        mappings.add(mapping("PROPERTY", "task.name", 11L, "tasks", "alternate_name"));
        OntologyDocument ambiguous = new OntologyDocument(
                base.key(), base.name(), base.description(), base.concepts(), base.relations(),
                base.metrics(), base.actions(), base.dataSources(), mappings);
        stubPublishedSnapshot(ambiguous, 1);
        SemanticQueryService service = serviceWith(new InlineSampleOntologyAdapter(objectMapper));

        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", querySelecting("name")))
                .hasMessage("MAPPING_AMBIGUOUS");
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

        verifyNoInteractions(workspaces, versions, auditWriter);
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
        SemanticQueryService service = serviceWith(adapter);

        assertThatThrownBy(() -> service.execute(
                "org-a", "user-a", querySelecting("name")))
                .hasMessage("CONNECTOR_READ_FAILED");

        ArgumentCaptor<OntologyQueryAuditEntity> audit =
                ArgumentCaptor.forClass(OntologyQueryAuditEntity.class);
        org.mockito.Mockito.verify(auditWriter).write(audit.capture());
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

    @Test
    void auditsPlanningFailureAfterWorkspaceAndVersionWereLocated() throws Exception {
        stubPublishedSnapshot(projectDeliverySnapshot(), 1);
        SemanticQueryService service = serviceWith(new InlineSampleOntologyAdapter(objectMapper));

        assertThatThrownBy(() -> service.execute(
                "org-a", "user-a", querySelecting("unknown")))
                .hasMessage("QUERY_FIELD_UNKNOWN");

        ArgumentCaptor<OntologyQueryAuditEntity> audit =
                ArgumentCaptor.forClass(OntologyQueryAuditEntity.class);
        org.mockito.Mockito.verify(auditWriter).write(audit.capture());
        assertThat(audit.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(audit.getValue().getErrorCode()).isEqualTo("QUERY_FIELD_UNKNOWN");
        assertThat(audit.getValue().getDataSourceId()).isNull();
    }

    @Test
    void joinsInlineProjectsToTasksAndReturnsNestedOneToManyResultsWithCompleteEvidence()
            throws Exception {
        stubPublishedSnapshot(projectTasksSnapshot(
                OntologyDocument.SourceType.INLINE_SAMPLE,
                inlineProjectTaskData(),
                "projects",
                "tasks"), 1);
        SemanticQueryService service = serviceWith(new InlineSampleOntologyAdapter(objectMapper));

        SemanticQueryService.QueryResult result = service.execute(
                "org-a",
                "user-a",
                new SemanticQueryService.SemanticQuery(
                        "project-delivery",
                        1,
                        "project",
                        List.of("name", "tasks.name", "tasks.status"),
                        List.of(new SemanticQueryService.Filter("phase", "EQ", "ACTIVE")),
                        List.of(new SemanticQueryService.OrderBy("name", "ASC")),
                        50));

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().get("name")).isEqualTo("Alpha");
        assertThat(result.rows().getFirst().get("tasks"))
                .isEqualTo(List.of(
                        java.util.Map.of("name", "Design", "status", "ACTIVE"),
                        java.util.Map.of("name", "Ship", "status", "DONE")));
        assertThat(result.evidence().mappings())
                .extracting(SemanticQueryService.MappingEvidence::usage)
                .contains(
                        "ROOT_OBJECT",
                        "SELECT",
                        "FILTER",
                        "ORDER",
                        "JOIN_SOURCE",
                        "TARGET_OBJECT",
                        "JOIN_TARGET");
        assertThat(result.evidence().mappings()).contains(
                new SemanticQueryService.MappingEvidence(
                        "name", "projects", "name", "SELECT"),
                new SemanticQueryService.MappingEvidence(
                        "phase", "projects", "phase", "FILTER"),
                new SemanticQueryService.MappingEvidence(
                        "name", "projects", "name", "ORDER"),
                new SemanticQueryService.MappingEvidence(
                        "tasks", "projects", "id", "JOIN_SOURCE"),
                new SemanticQueryService.MappingEvidence(
                        "tasks", "tasks", "project_id", "JOIN_TARGET"));
        assertThat(result.evidence().totalCount()).isEqualTo(1);
        assertThat(result.evidence().moreAvailable()).isFalse();
    }

    @Test
    void joinsCloudccProjectsToTasksWithExactlyOneRootAndOneTargetRead() throws Exception {
        stubPublishedSnapshot(projectTasksSnapshot(
                OntologyDocument.SourceType.CONNECTOR,
                "{\"adapterKey\":\"cloudcc\"}",
                "Project__c",
                "Task__c"), 1);
        CloudccOpenApiService cloudcc = mock(CloudccOpenApiService.class);
        when(cloudcc.pageQueryRecords(
                "org-a", "user-a", "Project__c", "name,id", "", 1, 50))
                .thenReturn(new CloudccOpenApiService.PageRecords(
                        List.of(java.util.Map.of("id", "p1", "name", "Alpha")),
                        1, 1, 1));
        when(cloudcc.pageQueryRecords(
                "org-a", "user-a", "Task__c", "project_id,name,status",
                "project_id in ('p1')", 1, 200))
                .thenReturn(new CloudccOpenApiService.PageRecords(
                        List.of(
                                java.util.Map.of(
                                        "project_id", "p1", "name", "Design", "status", "ACTIVE"),
                                java.util.Map.of(
                                        "project_id", "p1", "name", "Ship", "status", "DONE")),
                        1, 1, 2));
        SemanticQueryService service = serviceWith(
                new CloudccOntologyAdapter(cloudcc, objectMapper));

        SemanticQueryService.QueryResult result = service.execute(
                "org-a", "user-a", new SemanticQueryService.SemanticQuery(
                        "project-delivery", 1, "project",
                        List.of("name", "tasks.name", "tasks.status"),
                        List.of(), List.of(), 50));

        assertThat(result.rows().getFirst().get("tasks"))
                .isEqualTo(List.of(
                        java.util.Map.of("name", "Design", "status", "ACTIVE"),
                        java.util.Map.of("name", "Ship", "status", "DONE")));
        verify(cloudcc).pageQueryRecords(
                "org-a", "user-a", "Project__c", "name,id", "", 1, 50);
        verify(cloudcc).pageQueryRecords(
                "org-a", "user-a", "Task__c", "project_id,name,status",
                "project_id in ('p1')", 1, 200);
    }

    @Test
    void rejectsRelationFilterAndOrderWithStableErrorsBeforeSnapshotAccess() {
        SemanticQueryService service = serviceWith(mock(OntologyDataSourceAdapter.class));

        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", new SemanticQueryService.SemanticQuery(
                        "project-delivery", 1, "project", List.of("name"),
                        List.of(new SemanticQueryService.Filter(
                                "tasks.status", "EQ", "ACTIVE")),
                        List.of(), 50)))
                .hasMessage("RELATION_FILTER_NOT_SUPPORTED");
        assertThatThrownBy(() -> service.explain(
                "org-a", "user-a", new SemanticQueryService.SemanticQuery(
                        "project-delivery", 1, "project", List.of("name"),
                        List.of(),
                        List.of(new SemanticQueryService.OrderBy(
                                "tasks.name", "ASC")),
                        50)))
                .hasMessage("RELATION_ORDER_NOT_SUPPORTED");

        verifyNoInteractions(workspaces, versions, auditWriter);
    }

    @Test
    void failsClosedWhenATargetRelationReadIsTruncated() throws Exception {
        stubPublishedSnapshot(projectTasksSnapshot(
                OntologyDocument.SourceType.CONNECTOR,
                "{\"adapterKey\":\"bounded\"}",
                "projects",
                "tasks"), 1);
        OntologyDataSourceAdapter adapter = mock(OntologyDataSourceAdapter.class);
        when(adapter.supports(any())).thenReturn(true);
        when(adapter.executeRead(any(), any(), any()))
                .thenReturn(new PhysicalResult(
                        List.of(java.util.Map.of("name", "Alpha", "id", "p1")),
                        1,
                        false))
                .thenReturn(new PhysicalResult(List.of(), 201, true));
        SemanticQueryService service = serviceWith(adapter);

        assertThatThrownBy(() -> service.execute(
                "org-a", "user-a", new SemanticQueryService.SemanticQuery(
                        "project-delivery", 1, "project",
                        List.of("name", "tasks.name"),
                        List.of(), List.of(), 50)))
                .hasMessage("RELATION_RESULT_LIMIT_EXCEEDED");
    }

    @Test
    void shapesSingleValuedCardinalityAsANestedObject() throws Exception {
        OntologyDocument snapshot = withCardinality(
                projectTasksSnapshot(
                        OntologyDocument.SourceType.INLINE_SAMPLE,
                        """
                                {
                                  "projects":[{"id":"p1","name":"Alpha","phase":"ACTIVE"}],
                                  "tasks":[{"project_id":"p1","name":"Design","status":"ACTIVE"}]
                                }
                                """,
                        "projects",
                        "tasks"),
                OntologyDocument.Cardinality.MANY_TO_ONE);
        stubPublishedSnapshot(snapshot, 1);
        SemanticQueryService service = serviceWith(new InlineSampleOntologyAdapter(objectMapper));

        SemanticQueryService.QueryResult result = service.execute(
                "org-a", "user-a", new SemanticQueryService.SemanticQuery(
                        "project-delivery", 1, "project",
                        List.of("name", "tasks.name"),
                        List.of(), List.of(), 50));

        assertThat(result.rows().getFirst().get("tasks"))
                .isEqualTo(java.util.Map.of("name", "Design"));
    }

    @Test
    void rejectsDuplicateSourceJoinKeysForOneToOneCardinality() throws Exception {
        stubPublishedSnapshot(withCardinality(
                projectTasksSnapshot(
                        OntologyDocument.SourceType.CONNECTOR,
                        "{\"adapterKey\":\"bounded\"}",
                        "projects",
                        "tasks"),
                OntologyDocument.Cardinality.ONE_TO_ONE), 1);
        OntologyDataSourceAdapter adapter = mock(OntologyDataSourceAdapter.class);
        when(adapter.supports(any())).thenReturn(true);
        when(adapter.executeRead(any(), any(), any())).thenReturn(new PhysicalResult(
                List.of(
                        java.util.Map.of("name", "Alpha", "id", "p1"),
                        java.util.Map.of("name", "Duplicate", "id", "p1")),
                2,
                false));
        SemanticQueryService service = serviceWith(adapter);

        assertThatThrownBy(() -> service.execute(
                "org-a", "user-a", new SemanticQueryService.SemanticQuery(
                        "project-delivery", 1, "project",
                        List.of("name", "tasks.name"),
                        List.of(), List.of(), 50)))
                .hasMessage("RELATION_CARDINALITY_VIOLATION");

        verify(adapter, times(1)).executeRead(any(), any(), any());
    }

    @Test
    void batchesTwoHundredRootJoinKeysIntoAtMostOneHundredValueTargetFilters()
            throws Exception {
        stubPublishedSnapshot(projectTasksSnapshot(
                OntologyDocument.SourceType.CONNECTOR,
                "{\"adapterKey\":\"bounded\"}",
                "projects",
                "tasks"), 1);
        OntologyDataSourceAdapter adapter = mock(OntologyDataSourceAdapter.class);
        when(adapter.supports(any())).thenReturn(true);
        List<java.util.Map<String, Object>> roots = IntStream.range(0, 200)
                .mapToObj(index -> {
                    java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("name", "Project " + index);
                    row.put("id", "p" + index);
                    return row;
                })
                .toList();
        when(adapter.executeRead(any(), any(), any()))
                .thenReturn(new PhysicalResult(roots, 200, false))
                .thenReturn(new PhysicalResult(List.of(), 0, false))
                .thenReturn(new PhysicalResult(List.of(), 0, false));
        SemanticQueryService service = serviceWith(adapter);

        SemanticQueryService.QueryResult result = service.execute(
                "org-a", "user-a", new SemanticQueryService.SemanticQuery(
                        "project-delivery", 1, "project",
                        List.of("name", "tasks.name"),
                        List.of(), List.of(), 200));

        assertThat(result.rows()).hasSize(200);
        ArgumentCaptor<OntologyDataSourceAdapter.PhysicalQuery> queries =
                ArgumentCaptor.forClass(OntologyDataSourceAdapter.PhysicalQuery.class);
        verify(adapter, times(3)).executeRead(any(), any(), queries.capture());
        assertThat((List<?>) queries.getAllValues().get(1).filters().getFirst().value())
                .hasSize(100);
        assertThat((List<?>) queries.getAllValues().get(2).filters().getFirst().value())
                .hasSize(100);
    }

    private SemanticQueryService serviceWith(OntologyDataSourceAdapter adapter) {
        return new SemanticQueryService(
                workspaces,
                versions,
                auditWriter,
                List.of(adapter),
                objectMapper);
    }

    private SemanticQueryService.SemanticQuery querySelecting(String field) {
        return new SemanticQueryService.SemanticQuery(
                "project-delivery", 1, "task", List.of(field),
                List.of(), List.of(), 50);
    }

    private SemanticQueryService.SemanticQuery queryFiltering(String operator, Object value) {
        return new SemanticQueryService.SemanticQuery(
                "project-delivery", 1, "task", List.of("name"),
                List.of(new SemanticQueryService.Filter("status", operator, value)),
                List.of(), 50);
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

    private OntologyDocument projectTasksSnapshot(
            OntologyDocument.SourceType sourceType,
            String configJson,
            String projectObject,
            String taskObject) {
        OntologyDocument.DataSource source = new OntologyDocument.DataSource(
                11L, "delivery-source", "交付数据", sourceType, configJson);
        OntologyDocument.Concept project = new OntologyDocument.Concept(
                "project", "项目", "项目", "", OntologyDocument.ConceptType.ENTITY,
                "name", 0, 0, true, true,
                List.of(
                        property("name", false, true),
                        property("phase", false, true)));
        OntologyDocument.Concept task = new OntologyDocument.Concept(
                "task", "任务", "任务", "", OntologyDocument.ConceptType.EVENT,
                "name", 0, 0, true, true,
                List.of(
                        property("name", false, true),
                        property("status", false, true)));
        OntologyDocument.Relation tasks = new OntologyDocument.Relation(
                "tasks", "任务", "", "project", "task",
                OntologyDocument.Cardinality.ONE_TO_MANY,
                "任务", "项目", true, true);
        OntologyDocument.Mapping relation = new OntologyDocument.Mapping(
                "RELATION", "tasks", 11L, projectObject, "id", "project_id",
                "DIRECT", 1, "MANUAL", "VALID");
        return new OntologyDocument(
                "project-delivery", "项目交付", "",
                List.of(project, task),
                List.of(tasks),
                List.of(),
                List.of(),
                List.of(source),
                List.of(
                        mapping("CONCEPT", "project", 11L, projectObject, null),
                        mapping("PROPERTY", "project.name", 11L, projectObject, "name"),
                        mapping("PROPERTY", "project.phase", 11L, projectObject, "phase"),
                        mapping("CONCEPT", "task", 11L, taskObject, null),
                        mapping("PROPERTY", "task.name", 11L, taskObject, "name"),
                        mapping("PROPERTY", "task.status", 11L, taskObject, "status"),
                        relation));
    }

    private String inlineProjectTaskData() {
        return """
                {
                  "projects":[
                    {"id":"p2","name":"Beta","phase":"PAUSED"},
                    {"id":"p1","name":"Alpha","phase":"ACTIVE"}
                  ],
                  "tasks":[
                    {"project_id":"p1","name":"Design","status":"ACTIVE"},
                    {"project_id":"p1","name":"Ship","status":"DONE"},
                    {"project_id":"p2","name":"Backlog","status":"PLANNED"}
                  ]
                }
                """;
    }

    private OntologyDocument withCardinality(
            OntologyDocument document,
            OntologyDocument.Cardinality cardinality) {
        OntologyDocument.Relation relation = document.relations().getFirst();
        OntologyDocument.Relation changed = new OntologyDocument.Relation(
                relation.key(),
                relation.name(),
                relation.description(),
                relation.sourceConceptKey(),
                relation.targetConceptKey(),
                cardinality,
                relation.forwardLabel(),
                relation.reverseLabel(),
                relation.queryable(),
                relation.enabled());
        return new OntologyDocument(
                document.key(), document.name(), document.description(),
                document.concepts(), List.of(changed), document.metrics(),
                document.actions(), document.dataSources(), document.mappings());
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
