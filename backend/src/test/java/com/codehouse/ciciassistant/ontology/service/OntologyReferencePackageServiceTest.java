package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OntologyReferencePackageServiceTest {

    private final OntologyReferencePackageService service =
            new OntologyReferencePackageService(new ObjectMapper());

    @Test
    void listsAndStrictlyLoadsOrdinaryClasspathPackages() {
        assertThat(service.list())
                .extracting(OntologyReferencePackageService.ReferencePackageSummary::id)
                .containsExactly("customer-operations", "project-delivery");

        OntologyReferencePackageService.ReferencePackage project =
                service.load("project-delivery");

        assertThat(project.document().concepts())
                .extracting(OntologyDocument.Concept::key)
                .containsExactly("project", "task", "owner");
        assertThat(project.document().dataSources()).singleElement().satisfies(source -> {
            assertThat(source.id()).isNegative();
            assertThat(source.sampleDataJson()).contains("projects", "tasks", "owners");
        });
        assertThat(project.document().mappings())
                .allSatisfy(mapping -> {
                    assertThat(mapping.source()).isEqualTo("REFERENCE");
                    assertThat(mapping.validationStatus()).isEqualTo("PENDING");
                });
    }

    @Test
    void returnsNotFoundWithoutFallingBackToDomainSpecificLogic() {
        assertThatThrownBy(() -> service.load("missing-domain"))
                .isInstanceOf(com.codehouse.ciciassistant.common.error.ResourceNotFoundException.class)
                .hasMessage("ONTOLOGY_REFERENCE_PACKAGE_NOT_FOUND");
    }
}
