package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ontology.domain.OntologyAiProposalRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        TenantContext.setOrgId("org-a");
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
                        "unsafe-package", "Unsafe package", null, unsafe));
        doThrow(new IllegalArgumentException("ONTOLOGY_VALIDATION_FAILED"))
                .when(draftSafety).validateDocument(unsafe);

        assertThatThrownBy(() -> service.installReferencePackage("user-a", "unsafe-package"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ONTOLOGY_VALIDATION_FAILED");

        verify(draftSafety).validateDocument(unsafe);
        verifyNoInteractions(persistence, drafts);
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
}
