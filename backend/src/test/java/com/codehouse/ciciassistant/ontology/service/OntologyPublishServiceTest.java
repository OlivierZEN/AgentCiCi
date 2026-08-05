package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OntologyPublishServiceTest {

    @Mock
    private OntologyWorkspaceRepository workspaces;
    @Mock
    private OntologyDraftService drafts;
    @Mock
    private OntologyValidationService validation;
    @Mock
    private OntologyCompilerService compiler;
    @Mock
    private OntologyTenantPersistence persistence;
    @Mock
    private OntologyVersionRepository versions;
    @Mock
    private OntologyMappingRepository mappings;
    @Mock
    private OntologyMappingIntegrityService mappingIntegrity;

    private OntologyPublishService service;

    @BeforeEach
    void setUp() {
        service = new OntologyPublishService(
                workspaces,
                drafts,
                validation,
                compiler,
                persistence,
                versions,
                mappings,
                mappingIntegrity,
                new ObjectMapper());
        TenantContext.setCompanyId("org-a");
        TenantContext.setUserId("human-a");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void rejectsPublishWhenValidationContainsAnError() {
        OntologyWorkspaceEntity workspace = workspace(41L, 2L, null);
        OntologyDocument document = OntologyCompilerServiceTest.projectDeliveryDocument();
        when(workspaces.findForUpdateByIdAndCompanyId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));
        when(drafts.loadDraft("org-a", 41L, workspace)).thenReturn(document);
        when(validation.validate(document, true)).thenReturn(List.of(
                new OntologyValidationService.ValidationIssue(
                        "QUERYABLE_MAPPING_REQUIRED",
                        OntologyValidationService.Severity.ERROR,
                        "$.concepts[project]",
                        "missing mapping")));

        assertThatThrownBy(() -> service.publish("org-a", "human-a", 41L, 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("ONTOLOGY_VALIDATION_FAILED");

        verifyNoInteractions(compiler, persistence);
    }

    @Test
    void publishesImmutableCompiledSnapshotAndUpdatesWorkspaceState() {
        OntologyWorkspaceEntity workspace = workspace(41L, 2L, 3);
        OntologyDocument document = OntologyCompilerServiceTest.projectDeliveryDocument();
        OntologyCompilerService.CompiledContracts contracts =
                new OntologyCompilerService.CompiledContracts(
                        "hash-v4", "{\"snapshot\":true}", "{\"schema\":true}",
                        "type Query { project: Project }", "{\"query\":true}");
        when(workspaces.findForUpdateByIdAndCompanyId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));
        when(drafts.loadDraft("org-a", 41L, workspace)).thenReturn(document);
        when(validation.validate(document, true)).thenReturn(List.of());
        stubValidatedMappings(document);
        when(compiler.compile(document, 4)).thenReturn(contracts);
        when(persistence.saveForCurrentOrg(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OntologyVersionEntity version = service.publish("org-a", "human-a", 41L, 2L);

        assertThat(version.getVersionNo()).isEqualTo(4);
        assertThat(version.getSourceDraftRevision()).isEqualTo(2L);
        assertThat(version.getContentHash()).isEqualTo("hash-v4");
        assertThat(version.getSnapshotJson()).isEqualTo("{\"snapshot\":true}");
        assertThat(version.getValidationSummaryJson()).isEqualTo("[]");
        assertThat(workspace.getStatus()).isEqualTo("PUBLISHED");
        assertThat(workspace.getPublishedVersion()).isEqualTo(4);
        assertThat(workspace.getUpdatedBy()).isEqualTo("human-a");

        InOrder writes = inOrder(persistence);
        writes.verify(persistence).saveForCurrentOrg(version);
        writes.verify(persistence).saveForCurrentOrg(workspace);
    }

    @Test
    void returnsExistingVersionForSameDraftRevisionWithoutRepeatingSideEffects() {
        OntologyWorkspaceEntity workspace = workspace(41L, 2L, 1);
        OntologyVersionEntity existing = new OntologyVersionEntity(
                "org-a",
                41L,
                1,
                2L,
                "hash-v1",
                "{\"snapshot\":true}",
                "{\"schema\":true}",
                "type Query { project: Project }",
                "{\"query\":true}",
                "[]",
                "human-original");
        ReflectionTestUtils.setField(existing, "id", 501L);
        when(workspaces.findForUpdateByIdAndCompanyId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));
        when(versions.findByWorkspaceIdAndCompanyIdAndSourceDraftRevision(
                41L, "org-a", 2L))
                .thenReturn(Optional.of(existing));

        OntologyVersionEntity result = service.publish(
                "org-a", "human-a", 41L, 2L);

        assertThat(result).isSameAs(existing);
        assertThat(workspace.getPublishedVersion()).isEqualTo(1);
        verifyNoInteractions(drafts, validation, compiler, persistence, mappings, mappingIntegrity);
    }

    @Test
    void rejectsRevisionConflictBeforeLoadingOrWritingDraft() {
        OntologyWorkspaceEntity workspace = workspace(41L, 5L, 2);
        when(workspaces.findForUpdateByIdAndCompanyId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));

        assertThatThrownBy(() -> service.publish("org-a", "human-a", 41L, 4L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("ONTOLOGY_REVISION_CONFLICT");

        verify(drafts, never()).loadDraft(any(), any(), any());
        verifyNoInteractions(validation, compiler, persistence);
    }

    @Test
    void rejectsClientClaimedValidMappingWithoutFreshServerValidation() {
        OntologyWorkspaceEntity workspace = workspace(41L, 2L, null);
        OntologyDocument document = OntologyCompilerServiceTest.projectDeliveryDocument();
        when(workspaces.findForUpdateByIdAndCompanyId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));
        when(drafts.loadDraft("org-a", 41L, workspace)).thenReturn(document);
        when(validation.validate(document, true)).thenReturn(List.of());
        OntologyDocument.Mapping first = document.mappings().getFirst();
        OntologyMappingEntity pending = mapping(first);
        when(mappings.findByWorkspaceIdAndCompanyIdAndTargetTypeAndTargetKeyAndDataSourceId(
                41L, "org-a", first.targetType(), first.targetKey(), first.dataSourceId()))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.publish("org-a", "human-a", 41L, 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("ONTOLOGY_MAPPING_VALIDATION_REQUIRED");

        verifyNoInteractions(compiler, persistence);
    }

    @Test
    void exposesOnlyExplicitHumanPublishAndGovernedRollbackOperations() {
        assertThat(Arrays.stream(OntologyPublishService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(method -> method.getName()))
                .containsExactlyInAnyOrder("publish", "rollbackToPrevious");
    }

    @Test
    void rollsBackLocalPublishedPointerToExistingPreviousVersion() {
        OntologyWorkspaceEntity workspace = workspace(41L, 5L, 3);
        OntologyVersionEntity previous = new OntologyVersionEntity(
                "org-a", 41L, 2, 4L, "hash-v2", "{}", "{}", "", "{}", "[]", "human-a");
        when(workspaces.findForUpdateByIdAndCompanyId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));
        when(versions.findByWorkspaceIdAndCompanyIdAndVersionNo(41L, "org-a", 2))
                .thenReturn(Optional.of(previous));
        when(persistence.saveForCurrentOrg(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OntologyVersionEntity result = service.rollbackToPrevious("org-a", "human-a", 41L);

        assertThat(result).isSameAs(previous);
        assertThat(workspace.getPublishedVersion()).isEqualTo(2);
        assertThat(workspace.getUpdatedBy()).isEqualTo("human-a");
        verify(persistence).saveForCurrentOrg(workspace);
    }

    @Test
    void rejectsBlankOrImpersonatedPublisherBeforeReadingWorkspace() {
        assertThatThrownBy(() -> service.publish("org-a", " ", 41L, 1L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ONTOLOGY_PUBLISH_REQUIRES_HUMAN");
        assertThatThrownBy(() -> service.publish("org-a", "another-user", 41L, 1L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ONTOLOGY_PUBLISH_REQUIRES_HUMAN");

        verifyNoInteractions(workspaces, drafts, validation, compiler, persistence);
    }

    private OntologyWorkspaceEntity workspace(
            Long id,
            Long draftRevision,
            Integer publishedVersion) {
        OntologyWorkspaceEntity workspace = new OntologyWorkspaceEntity(
                "org-a", "project-delivery", "项目交付", "", "creator");
        ReflectionTestUtils.setField(workspace, "id", id);
        ReflectionTestUtils.setField(workspace, "draftRevision", draftRevision);
        ReflectionTestUtils.setField(workspace, "publishedVersion", publishedVersion);
        return workspace;
    }

    private void stubValidatedMappings(OntologyDocument document) {
        for (OntologyDocument.Mapping value : document.mappings()) {
            OntologyMappingEntity entity = mapping(value);
            entity.applyValidation(true);
            when(mappings.findByWorkspaceIdAndCompanyIdAndTargetTypeAndTargetKeyAndDataSourceId(
                    41L, "org-a", value.targetType(), value.targetKey(), value.dataSourceId()))
                    .thenReturn(Optional.of(entity));
            when(mappingIntegrity.validate("org-a", 41L, document, value))
                    .thenReturn(com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.MappingValidation.success());
            when(mappingIntegrity.isFresh(
                    "org-a", 41L, document, value, entity.getLastValidatedAt()))
                    .thenReturn(true);
        }
    }

    private OntologyMappingEntity mapping(OntologyDocument.Mapping value) {
        return new OntologyMappingEntity(
                "org-a",
                41L,
                value.targetType(),
                value.targetKey(),
                value.dataSourceId(),
                value.physicalObjectKey(),
                value.physicalFieldKey(),
                value.relationTargetFieldKey(),
                value.transform(),
                BigDecimal.valueOf(value.confidence()),
                "MANUAL",
                "PENDING",
                "human-a");
    }
}
