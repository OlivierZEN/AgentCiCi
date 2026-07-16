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
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Modifier;
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

    private OntologyPublishService service;

    @BeforeEach
    void setUp() {
        service = new OntologyPublishService(
                workspaces,
                drafts,
                validation,
                compiler,
                persistence,
                new ObjectMapper());
        TenantContext.setOrgId("org-a");
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
        when(workspaces.findForUpdateByIdAndOrgId(41L, "org-a"))
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
        when(workspaces.findForUpdateByIdAndOrgId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));
        when(drafts.loadDraft("org-a", 41L, workspace)).thenReturn(document);
        when(validation.validate(document, true)).thenReturn(List.of());
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
    void rejectsRevisionConflictBeforeLoadingOrWritingDraft() {
        OntologyWorkspaceEntity workspace = workspace(41L, 5L, 2);
        when(workspaces.findForUpdateByIdAndOrgId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));

        assertThatThrownBy(() -> service.publish("org-a", "human-a", 41L, 4L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("ONTOLOGY_REVISION_CONFLICT");

        verify(drafts, never()).loadDraft(any(), any(), any());
        verifyNoInteractions(validation, compiler, persistence);
    }

    @Test
    void exposesOnlyTheExplicitHumanPublishOperation() {
        assertThat(Arrays.stream(OntologyPublishService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(method -> method.getName()))
                .containsExactly("publish");
    }

    @Test
    void rejectsBlankOrImpersonatedPublisherBeforeReadingWorkspace() {
        assertThatThrownBy(() -> service.publish("org-a", " ", 41L, 1L))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.publish("org-a", "another-user", 41L, 1L))
                .isInstanceOf(ForbiddenException.class);

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
}
