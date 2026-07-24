package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.ontology.domain.AbstractOntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyActionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyActionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyConceptEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyConceptRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyMetricEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyMetricRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPropertyEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyPropertyRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyRelationEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyRelationRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OntologyDraftServiceTest {

    @Mock
    private OntologyWorkspaceRepository workspaces;
    @Mock
    private OntologyConceptRepository concepts;
    @Mock
    private OntologyPropertyRepository properties;
    @Mock
    private OntologyRelationRepository relations;
    @Mock
    private OntologyMetricRepository metrics;
    @Mock
    private OntologyActionRepository actions;
    @Mock
    private OntologyDataSourceRepository dataSources;
    @Mock
    private OntologyPhysicalFieldRepository physicalFields;
    @Mock
    private OntologyPhysicalObjectRepository physicalObjects;
    @Mock
    private OntologyMappingRepository mappings;
    @Mock
    private OntologyTenantPersistence persistence;
    @Mock
    private OntologyDataSourcePolicy dataSourcePolicy;
    @Mock
    private OntologyDraftSafetyPolicy draftSafety;

    private OntologyDraftService service;

    @BeforeEach
    void setUp() {
        service = new OntologyDraftService(
                workspaces,
                concepts,
                properties,
                relations,
                metrics,
                actions,
                dataSources,
                physicalObjects,
                mappings,
                persistence,
                dataSourcePolicy,
                draftSafety,
                new ObjectMapper());
        TenantContext.setCompanyId("org-a");
    }

    @Test
    void rejectsUnsafeDocumentBeforeWorkspaceLookupOrAnyPersistenceWork() {
        OntologyDocument invalid = new OntologyDocument(
                "unsafe", "不安全", null, null,
                List.of(), List.of(), List.of(), List.of(), List.of());
        doThrow(new IllegalArgumentException("ONTOLOGY_VALIDATION_FAILED"))
                .when(draftSafety).validateDocument(invalid);

        assertThatThrownBy(() -> service.saveDraft(
                "org-a", "user-a", 41L, 0L, invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ONTOLOGY_VALIDATION_FAILED");

        verifyNoInteractions(workspaces, persistence, concepts, properties, relations,
                metrics, actions, dataSources, physicalObjects, mappings);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void rejectsRevisionConflictBeforeDeletingOrWritingAnything() {
        OntologyWorkspaceEntity workspace = workspace(41L, 3L);
        when(workspaces.findForUpdateByIdAndCompanyId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));

        assertThatThrownBy(() -> service.saveDraft(
                "org-a", "user-a", 41L, 2L,
                OntologyCompilerServiceTest.projectDeliveryDocument()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("ONTOLOGY_REVISION_CONFLICT");

        verifyNoInteractions(persistence);
        verify(mappings, never()).deleteByWorkspaceIdAndCompanyId(any(), any());
        verify(concepts, never()).deleteByWorkspaceIdAndCompanyId(any(), any());
    }

    @Test
    void replacesDraftChildrenAndRoutesEveryWriteThroughTenantPersistence() {
        OntologyWorkspaceEntity workspace = workspace(41L, 0L);
        when(workspaces.findForUpdateByIdAndCompanyId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));
        OntologyDataSourceEntity existingSource = new OntologyDataSourceEntity(
                "org-a",
                41L,
                "delivery-source",
                "旧交付数据",
                "INLINE_SAMPLE",
                "{\"old\":true}",
                "[{\"id\":1}]",
                "creator");
        ReflectionTestUtils.setField(existingSource, "id", 99L);
        when(dataSources.findByWorkspaceIdAndCompanyIdAndKey(
                41L, "org-a", "delivery-source"))
                .thenReturn(Optional.of(existingSource));
        AtomicLong ids = new AtomicLong(100);
        when(persistence.saveForCurrentOrg(any())).thenAnswer(invocation -> {
            OntologyTenantEntity entity = invocation.getArgument(0);
            if (entity instanceof AbstractOntologyWorkspaceEntity child && child.getId() == null) {
                ReflectionTestUtils.setField(child, "id", ids.incrementAndGet());
            }
            return entity;
        });

        OntologyWorkspaceEntity saved = service.saveDraft(
                "org-a", "user-a", 41L, 0L,
                OntologyCompilerServiceTest.projectDeliveryDocument());

        assertThat(saved.getDraftRevision()).isEqualTo(1L);
        assertThat(saved.getKey()).isEqualTo("project-delivery");
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        verify(mappings, never()).deleteByWorkspaceIdAndCompanyId(41L, "org-a");
        verify(physicalFields, never()).deleteByWorkspaceIdAndCompanyId(41L, "org-a");
        ArgumentCaptor<Runnable> catalogDelete = ArgumentCaptor.forClass(Runnable.class);
        verify(persistence).deleteForCurrentOrg(eq("org-a"), catalogDelete.capture());
        catalogDelete.getValue().run();
        verify(physicalObjects).deleteByDataSourceIdAndWorkspaceIdAndCompanyId(99L, 41L, "org-a");
        verify(properties).deleteByWorkspaceIdAndCompanyId(41L, "org-a");
        verify(relations).deleteByWorkspaceIdAndCompanyId(41L, "org-a");
        verify(metrics).deleteByWorkspaceIdAndCompanyId(41L, "org-a");
        verify(actions).deleteByWorkspaceIdAndCompanyId(41L, "org-a");
        verify(concepts).deleteByWorkspaceIdAndCompanyId(41L, "org-a");
        verify(dataSources, never()).deleteByWorkspaceIdAndCompanyId(41L, "org-a");
        verify(persistence).flushForCurrentOrg("org-a");

        verify(persistence, times(2)).saveForCurrentOrg(isA(OntologyConceptEntity.class));
        verify(persistence, times(3)).saveForCurrentOrg(isA(OntologyPropertyEntity.class));
        verify(persistence).saveForCurrentOrg(isA(OntologyRelationEntity.class));
        verify(persistence).saveForCurrentOrg(isA(OntologyMetricEntity.class));
        verify(persistence).saveForCurrentOrg(isA(OntologyActionEntity.class));
        verify(persistence).saveForCurrentOrg(isA(OntologyDataSourceEntity.class));
        verify(persistence, times(6)).saveForCurrentOrg(isA(OntologyMappingEntity.class));
        verify(persistence, atLeastOnce()).saveForCurrentOrg(workspace);

        ArgumentCaptor<OntologyDataSourceEntity> sourceCaptor =
                ArgumentCaptor.forClass(OntologyDataSourceEntity.class);
        verify(persistence).saveForCurrentOrg(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue()).isSameAs(existingSource);
        assertThat(sourceCaptor.getValue().getSampleDataJson())
                .contains("\"projects\"")
                .contains("\"tasks\"");
        assertThat(sourceCaptor.getValue().getName()).isEqualTo("交付数据");
        ArgumentCaptor<OntologyMappingEntity> mappingCaptor =
                ArgumentCaptor.forClass(OntologyMappingEntity.class);
        verify(persistence, times(6)).saveForCurrentOrg(mappingCaptor.capture());
        assertThat(mappingCaptor.getAllValues())
                .extracting(OntologyMappingEntity::getDataSourceId)
                .containsOnly(sourceCaptor.getValue().getId());
    }

    @Test
    void preservesStableMappingIdentityAndServerValidationUntilDefinitionChanges() {
        OntologyWorkspaceEntity workspace = workspace(41L, 0L);
        when(workspaces.findForUpdateByIdAndCompanyId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));
        OntologyDocument.DataSource unchangedSource =
                OntologyCompilerServiceTest.projectDeliveryDocument().dataSources().getFirst();
        OntologyDataSourceEntity existingSource = new OntologyDataSourceEntity(
                "org-a", 41L, "delivery-source", "交付数据", "INLINE_SAMPLE",
                unchangedSource.configJson(), unchangedSource.sampleDataJson(), "creator");
        ReflectionTestUtils.setField(existingSource, "id", 99L);
        when(dataSources.findByIdAndWorkspaceIdAndCompanyId(1L, 41L, "org-a"))
                .thenReturn(Optional.empty());
        when(dataSources.findByWorkspaceIdAndCompanyIdAndKey(41L, "org-a", "delivery-source"))
                .thenReturn(Optional.of(existingSource));
        OntologyMappingEntity validated = new OntologyMappingEntity(
                "org-a", 41L, "PROPERTY", "project.name", 99L,
                "projects", "name", null, "DIRECT",
                java.math.BigDecimal.ONE, "MANUAL", "PENDING", "creator");
        ReflectionTestUtils.setField(validated, "id", 501L);
        validated.applyValidation(true);
        when(mappings.findByWorkspaceIdAndCompanyIdOrderByIdAsc(41L, "org-a"))
                .thenReturn(List.of(validated));
        AtomicLong ids = new AtomicLong(600L);
        when(persistence.saveForCurrentOrg(any())).thenAnswer(invocation -> {
            OntologyTenantEntity entity = invocation.getArgument(0);
            if (entity instanceof AbstractOntologyWorkspaceEntity child && child.getId() == null) {
                ReflectionTestUtils.setField(child, "id", ids.incrementAndGet());
            }
            return entity;
        });

        service.saveDraft(
                "org-a", "user-a", 41L, 0L,
                OntologyCompilerServiceTest.projectDeliveryDocument());

        assertThat(validated.getId()).isEqualTo(501L);
        assertThat(validated.getValidationStatus()).isEqualTo("VALID");
        assertThat(validated.getLastValidatedAt()).isNotNull();
        verify(mappings, never()).deleteByIdAndWorkspaceIdAndCompanyId(501L, 41L, "org-a");

        OntologyDocument current = OntologyCompilerServiceTest.projectDeliveryDocument();
        List<OntologyDocument.Mapping> changedMappings = current.mappings().stream()
                .map(mapping -> "project.name".equals(mapping.targetKey())
                        ? new OntologyDocument.Mapping(
                                mapping.targetType(), mapping.targetKey(), mapping.dataSourceId(),
                                mapping.physicalObjectKey(), "renamed_name", mapping.relationTargetFieldKey(),
                                mapping.transform(), mapping.confidence(), "AI", "VALID")
                        : mapping)
                .toList();
        OntologyDocument changed = new OntologyDocument(
                current.key(), current.name(), current.description(), current.concepts(),
                current.relations(), current.metrics(), current.actions(), current.dataSources(), changedMappings);
        ReflectionTestUtils.setField(workspace, "draftRevision", 1L);

        service.saveDraft("org-a", "user-a", 41L, 1L, changed);

        assertThat(validated.getId()).isEqualTo(501L);
        assertThat(validated.getPhysicalFieldKey()).isEqualTo("renamed_name");
        assertThat(validated.getSource()).isEqualTo("AI");
        assertThat(validated.getValidationStatus()).isEqualTo("PENDING");
        assertThat(validated.getLastValidatedAt()).isNull();
    }

    @Test
    void persistsChangedRelationEndpointsAndRequestedJoinDefinitionInTheSameRevision() {
        OntologyWorkspaceEntity workspace = workspace(41L, 0L);
        when(workspaces.findForUpdateByIdAndCompanyId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));
        OntologyDocument base = OntologyCompilerServiceTest.projectDeliveryDocument();
        OntologyDocument.DataSource source = base.dataSources().getFirst();
        OntologyDataSourceEntity existingSource = new OntologyDataSourceEntity(
                "org-a", 41L, source.key(), source.name(), source.type().name(),
                source.configJson(), source.sampleDataJson(), "creator");
        ReflectionTestUtils.setField(existingSource, "id", 99L);
        when(dataSources.findByIdAndWorkspaceIdAndCompanyId(1L, 41L, "org-a"))
                .thenReturn(Optional.empty());
        when(dataSources.findByWorkspaceIdAndCompanyIdAndKey(41L, "org-a", source.key()))
                .thenReturn(Optional.of(existingSource));

        OntologyConceptEntity oldProject = new OntologyConceptEntity(
                "org-a", 41L, "project", "项目", "项目", null,
                "ENTITY", "name", 0, 0, true, true);
        OntologyConceptEntity oldTask = new OntologyConceptEntity(
                "org-a", 41L, "task", "任务", "任务", null,
                "EVENT", "status", 0, 0, true, true);
        ReflectionTestUtils.setField(oldProject, "id", 10L);
        ReflectionTestUtils.setField(oldTask, "id", 11L);
        when(concepts.findByWorkspaceIdAndCompanyIdOrderByIdAsc(41L, "org-a"))
                .thenReturn(List.of(oldProject, oldTask));
        OntologyRelationEntity oldRelation = new OntologyRelationEntity(
                "org-a", 41L, "contains-task", "包含任务", null,
                10L, 11L, "ONE_TO_MANY", "包含", "属于", true, true);
        ReflectionTestUtils.setField(oldRelation, "id", 20L);
        when(relations.findByWorkspaceIdAndCompanyIdOrderByIdAsc(41L, "org-a"))
                .thenReturn(List.of(oldRelation));

        OntologyMappingEntity relationMapping = new OntologyMappingEntity(
                "org-a", 41L, "RELATION", "contains-task", 99L,
                "projects", "old_source_id", "old_target_id", "DIRECT",
                java.math.BigDecimal.ONE, "MANUAL", "PENDING", "creator");
        ReflectionTestUtils.setField(relationMapping, "id", 501L);
        relationMapping.applyValidation(true);
        when(mappings.findByWorkspaceIdAndCompanyIdOrderByIdAsc(41L, "org-a"))
                .thenReturn(List.of(relationMapping));
        AtomicLong ids = new AtomicLong(600L);
        when(persistence.saveForCurrentOrg(any())).thenAnswer(invocation -> {
            OntologyTenantEntity entity = invocation.getArgument(0);
            if (entity instanceof AbstractOntologyWorkspaceEntity child && child.getId() == null) {
                ReflectionTestUtils.setField(child, "id", ids.incrementAndGet());
            }
            return entity;
        });

        OntologyDocument.Relation reversedRelation = new OntologyDocument.Relation(
                "contains-task", "包含任务", "同轮变更端点",
                "task", "project", OntologyDocument.Cardinality.MANY_TO_ONE,
                "属于", "包含", true, true);
        List<OntologyDocument.Mapping> changedMappings = base.mappings().stream()
                .map(mapping -> "contains-task".equals(mapping.targetKey())
                        ? new OntologyDocument.Mapping(
                                mapping.targetType(), mapping.targetKey(), mapping.dataSourceId(),
                                "tasks", "project_id", "id", mapping.transform(),
                                mapping.confidence(), mapping.source(), mapping.validationStatus())
                        : mapping)
                .toList();
        OntologyDocument changed = new OntologyDocument(
                base.key(), base.name(), base.description(), base.concepts(),
                List.of(reversedRelation), base.metrics(), base.actions(),
                base.dataSources(), changedMappings);

        OntologyWorkspaceEntity saved = service.saveDraft(
                "org-a", "user-a", 41L, 0L, changed);

        assertThat(saved.getDraftRevision()).isEqualTo(1L);
        assertThat(relationMapping.getPhysicalObjectKey()).isEqualTo("tasks");
        assertThat(relationMapping.getPhysicalFieldKey()).isEqualTo("project_id");
        assertThat(relationMapping.getRelationTargetFieldKey()).isEqualTo("id");
        assertThat(relationMapping.getValidationStatus()).isEqualTo("PENDING");
        assertThat(relationMapping.getLastValidatedAt()).isNull();
        verify(persistence).saveForCurrentOrg(isA(OntologyRelationEntity.class));
    }

    @Test
    void rejectsArchivedWorkspaceAndImmutableKeyChangesBeforeMutatingChildren() {
        OntologyWorkspaceEntity archived = workspace(41L, 0L);
        ReflectionTestUtils.setField(archived, "status", "ARCHIVED");
        when(workspaces.findForUpdateByIdAndCompanyId(41L, "org-a"))
                .thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.saveDraft(
                "org-a", "user-a", 41L, 0L,
                OntologyCompilerServiceTest.projectDeliveryDocument()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("ONTOLOGY_WORKSPACE_ARCHIVED");

        OntologyWorkspaceEntity active = workspace(42L, 0L);
        when(workspaces.findForUpdateByIdAndCompanyId(42L, "org-a"))
                .thenReturn(Optional.of(active));
        OntologyDocument original = OntologyCompilerServiceTest.projectDeliveryDocument();
        OntologyDocument changedKey = new OntologyDocument(
                "renamed-key",
                original.name(),
                original.description(),
                original.concepts(),
                original.relations(),
                original.metrics(),
                original.actions(),
                original.dataSources(),
                original.mappings());

        assertThatThrownBy(() -> service.saveDraft(
                "org-a", "user-a", 42L, 0L, changedKey))
                .isInstanceOf(ConflictException.class)
                .hasMessage("ONTOLOGY_KEY_IMMUTABLE");

        verify(mappings, never()).deleteByWorkspaceIdAndCompanyId(any(), any());
        verify(concepts, never()).deleteByWorkspaceIdAndCompanyId(any(), any());
    }

    private OntologyWorkspaceEntity workspace(Long id, Long draftRevision) {
        OntologyWorkspaceEntity workspace = new OntologyWorkspaceEntity(
                "org-a", "project-delivery", "旧名称", "旧描述", "creator");
        ReflectionTestUtils.setField(workspace, "id", id);
        ReflectionTestUtils.setField(workspace, "draftRevision", draftRevision);
        return workspace;
    }
}
