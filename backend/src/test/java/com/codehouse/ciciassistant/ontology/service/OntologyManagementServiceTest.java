package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.ontology.domain.OntologyAiProposalRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class OntologyManagementServiceTest {

    @Mock private OntologyWorkspaceRepository workspaces;
    @Mock private OntologyDataSourceRepository dataSources;
    @Mock private OntologyMappingRepository mappings;
    @Mock private OntologyPhysicalObjectRepository objects;
    @Mock private OntologyPhysicalFieldRepository fields;
    @Mock private OntologyAiProposalRepository proposals;
    @Mock private OntologyVersionRepository versions;
    @Mock private OntologyTenantPersistence persistence;
    @Mock private OntologyDraftService drafts;
    @Mock private OntologyValidationService validation;
    @Mock private OntologyCompilerService compiler;
    @Mock private OntologyPublishService publisher;
    @Mock private OntologyAiProposalService aiProposals;
    @Mock private OntologyCatalogService catalog;
    @Mock private OntologyReferencePackageService referencePackages;
    @Mock private OntologyDataSourcePolicy dataSourcePolicy;
    @Mock private OntologyDraftSafetyPolicy draftSafety;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private OntologyManagementService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId("org-a");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void rejectsUnsafeReferencePackageBeforeCreatingWorkspace() {
        OntologyDocument unsafe = new OntologyDocument(
                "unsafe-package",
                "Unsafe package",
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        when(referencePackages.load("unsafe-package"))
                .thenReturn(new OntologyReferencePackageService.ReferencePackage(
                        "unsafe-package", "Unsafe package", null, unsafe, "0".repeat(64)));
        doThrow(new IllegalArgumentException("ONTOLOGY_VALIDATION_FAILED"))
                .when(draftSafety).validateDocument(unsafe);

        assertThatThrownBy(() -> service.installReferencePackage("user-a", "unsafe-package"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ONTOLOGY_VALIDATION_FAILED");

        verify(draftSafety).validateDocument(unsafe);
        verifyNoInteractions(persistence, drafts);
    }

    @Test
    void createsOrdinaryWorkspaceWithExplicitManualProvenance() {
        OntologyManagementService.WorkspaceCreateRequest request =
                new OntologyManagementService.WorkspaceCreateRequest(
                        "project-delivery", "项目交付", "统一交付语义");
        AtomicReference<OntologyWorkspaceEntity> persisted = new AtomicReference<>();
        when(persistence.saveForCurrentOrg(any(OntologyWorkspaceEntity.class)))
                .thenAnswer(invocation -> {
                    OntologyWorkspaceEntity workspace = invocation.getArgument(0);
                    persisted.set(workspace);
                    return workspace;
                });

        OntologyManagementService.WorkspaceView created =
                service.createWorkspace("user-a", request);

        assertThat(persisted.get().getCreationSource()).isEqualTo("MANUAL");
        assertThat(persisted.get().getReferencePackageId()).isNull();
        assertThat(persisted.get().getReferencePackageFingerprint()).isNull();
        assertThat(created.creationSource()).isEqualTo("MANUAL");
        assertThat(created.referencePackageId()).isNull();
        assertThat(created.referencePackageFingerprint()).isNull();
    }

    @Test
    void installsReferencePackageWithExplicitPackageProvenance() {
        OntologyDocument document = new OntologyDocument(
                "project-delivery",
                "项目交付",
                "统一交付语义",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        String fingerprint = "a".repeat(64);
        when(referencePackages.load("project-delivery"))
                .thenReturn(new OntologyReferencePackageService.ReferencePackage(
                        "project-delivery", "项目交付参考包", "参考包", document, fingerprint));
        AtomicReference<OntologyWorkspaceEntity> persisted = new AtomicReference<>();
        when(persistence.saveForCurrentOrg(any(OntologyWorkspaceEntity.class)))
                .thenAnswer(invocation -> {
                    OntologyWorkspaceEntity workspace = invocation.getArgument(0);
                    persisted.set(workspace);
                    return workspace;
                });
        when(drafts.saveDraft(
                eq("org-a"), eq("user-a"), isNull(), eq(0L), eq(document)))
                .thenAnswer(invocation -> persisted.get());

        OntologyManagementService.WorkspaceView installed =
                service.installReferencePackage("user-a", "project-delivery");

        assertThat(persisted.get().getCreationSource()).isEqualTo("REFERENCE_PACKAGE");
        assertThat(persisted.get().getReferencePackageId()).isEqualTo("project-delivery");
        assertThat(persisted.get().getReferencePackageFingerprint()).isEqualTo(fingerprint);
        assertThat(installed.creationSource()).isEqualTo("REFERENCE_PACKAGE");
        assertThat(installed.referencePackageId()).isEqualTo("project-delivery");
        assertThat(installed.referencePackageFingerprint()).isEqualTo(fingerprint);
    }

    @Test
    void rejectsUnsafeWorkspaceMetadataBeforeAnyLookupOrPersistence() {
        OntologyManagementService.WorkspaceCreateRequest request =
                new OntologyManagementService.WorkspaceCreateRequest(
                        "unsafe-workspace", "Unsafe workspace", "x".repeat(65_537));
        doThrow(new IllegalArgumentException("ONTOLOGY_VALIDATION_FAILED"))
                .when(draftSafety).validateWorkspaceMetadata(
                        request.key(), request.name(), request.description());

        assertThatThrownBy(() -> service.createWorkspace("user-a", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ONTOLOGY_VALIDATION_FAILED");

        verify(draftSafety).validateWorkspaceMetadata(
                request.key(), request.name(), request.description());
        verifyNoInteractions(workspaces, persistence);
    }

    @Test
    void translatesLateWorkspaceUniqueViolationToStableKeyConflict() {
        OntologyManagementService.WorkspaceCreateRequest request =
                new OntologyManagementService.WorkspaceCreateRequest(
                        "project-delivery", "项目交付", "统一交付语义");
        OntologyWorkspaceEntity saved = new OntologyWorkspaceEntity(
                "org-a", request.key(), request.name(), request.description(), "user-a");
        when(persistence.saveForCurrentOrg(any(OntologyWorkspaceEntity.class)))
                .thenReturn(saved);
        DataIntegrityViolationException uniqueViolation = new DataIntegrityViolationException(
                "workspace key conflict",
                new ConstraintViolationException(
                        "workspace key conflict",
                        new SQLException("duplicate key", "23505"),
                        "insert into ontology_workspace",
                        "uq_ontology_workspace_org_key"));
        doThrow(uniqueViolation)
                .when(persistence).flushForCurrentOrg("org-a");

        assertThatThrownBy(() -> service.createWorkspace("user-a", request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("ONTOLOGY_KEY_CONFLICT");

        verify(persistence).flushForCurrentOrg("org-a");
    }

    @Test
    void doesNotMislabelAnUnrelatedIntegrityViolationAsAWorkspaceKeyConflict() {
        OntologyManagementService.WorkspaceCreateRequest request =
                new OntologyManagementService.WorkspaceCreateRequest(
                        "project-delivery", "项目交付", "统一交付语义");
        OntologyWorkspaceEntity saved = new OntologyWorkspaceEntity(
                "org-a", request.key(), request.name(), request.description(), "user-a");
        when(persistence.saveForCurrentOrg(any(OntologyWorkspaceEntity.class)))
                .thenReturn(saved);
        DataIntegrityViolationException unrelatedViolation = new DataIntegrityViolationException(
                "unrelated constraint",
                new ConstraintViolationException(
                        "unrelated constraint",
                        new SQLException("null value", "23502"),
                        "insert into ontology_workspace",
                        "ontology_workspace_name_not_null"));
        doThrow(unrelatedViolation).when(persistence).flushForCurrentOrg("org-a");

        assertThatThrownBy(() -> service.createWorkspace("user-a", request))
                .isSameAs(unrelatedViolation);
    }
}
