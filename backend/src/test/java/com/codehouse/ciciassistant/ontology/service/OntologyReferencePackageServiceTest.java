package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OntologyReferencePackageServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OntologyReferencePackageService service =
            new OntologyReferencePackageService(objectMapper);

    @Test
    void listsAndStrictlyLoadsOrdinaryClasspathPackages() {
        List<OntologyReferencePackageService.ReferencePackageSummary> summaries =
                service.list();
        assertThat(summaries)
                .extracting(OntologyReferencePackageService.ReferencePackageSummary::id)
                .containsExactly("customer-operations", "project-delivery");
        assertThat(summaries)
                .filteredOn(summary -> summary.id().equals("project-delivery"))
                .singleElement()
                .extracting(OntologyReferencePackageService.ReferencePackageSummary::workspaceIdentity)
                .isEqualTo(new OntologyReferencePackageService.WorkspaceIdentity(
                        "project-delivery",
                        "项目交付",
                        "领域中立的项目交付参考本体"));

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
    void loadsCustomerOperationsAsCompletePendingPeripheralContract() throws Exception {
        OntologyDocument document = service.load("customer-operations").document();

        assertThat(document.concepts())
                .extracting(OntologyDocument.Concept::key)
                .containsExactly(
                        "customer",
                        "contact",
                        "opportunity",
                        "product",
                        "interaction",
                        "business_action");
        assertThat(document.concepts()).allSatisfy(concept -> {
            assertThat(concept.properties()).isNotEmpty();
            assertThat(concept.properties())
                    .extracting(OntologyDocument.Property::key)
                    .contains(concept.displayPropertyKey());
        });

        Set<String> expectedMappingTargets = document.concepts().stream()
                .flatMap(concept -> Stream.concat(
                        Stream.of("CONCEPT:" + concept.key()),
                        concept.properties().stream().map(property ->
                                "PROPERTY:" + concept.key() + "." + property.key())))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        document.relations().stream()
                .map(relation -> "RELATION:" + relation.key())
                .forEach(expectedMappingTargets::add);
        Set<String> actualMappingTargets = document.mappings().stream()
                .map(mapping -> mapping.targetType() + ":" + mapping.targetKey())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(actualMappingTargets)
                .containsExactlyInAnyOrderElementsOf(expectedMappingTargets);
        assertThat(document.mappings()).allSatisfy(mapping -> {
            assertThat(mapping.source()).isEqualTo("REFERENCE");
            assertThat(mapping.validationStatus()).isEqualTo("PENDING");
        });
        assertThat(document.dataSources()).singleElement().satisfies(source -> {
            assertThat(source.type()).isEqualTo(OntologyDocument.SourceType.CONNECTOR);
            assertThat(source.id()).isNegative();
            assertThat(source.sampleDataJson()).isNull();
        });

        assertThat(document.actions()).singleElement().satisfies(action -> {
            assertThat(action.key()).isEqualTo("plan-follow-up");
            assertThat(action.conceptKey()).isEqualTo("business_action");
            assertThat(action.description()).contains("只编译", "不执行");
            assertThat(action.parameters()).isNotEmpty();
        });
        OntologyCompilerService.CompiledContracts contracts =
                new OntologyCompilerService(objectMapper).compile(document, 1);
        JsonNode snapshot = objectMapper.readTree(contracts.snapshotJson());
        assertThat(snapshot.path("actions").path(0).path("key").asText())
                .isEqualTo("plan-follow-up");
        assertThat(contracts.graphqlSdl()).doesNotContain("type Mutation", "planFollowUp");
        assertThat(objectMapper.readTree(contracts.queryContractJson())
                .path("writeOperations"))
                .isEmpty();

        OntologyValidationService validator = new OntologyValidationService();
        assertThat(validator.validate(document, false)).isEmpty();
        long expectedPublishMappings = document.concepts().stream()
                .filter(concept -> concept.enabled() && concept.queryable())
                .mapToLong(concept -> 1 + concept.properties().stream()
                        .filter(property -> property.queryable() && !property.sensitive())
                        .count())
                .sum()
                + document.relations().stream()
                        .filter(relation -> relation.enabled() && relation.queryable())
                        .count();
        assertThat(validator.validate(document, true))
                .hasSize((int) expectedPublishMappings)
                .allSatisfy(issue ->
                        assertThat(issue.code()).isEqualTo("QUERYABLE_MAPPING_REQUIRED"));
    }

    @Test
    void returnsNotFoundWithoutFallingBackToDomainSpecificLogic() {
        assertThatThrownBy(() -> service.load("missing-domain"))
                .isInstanceOf(com.codehouse.ciciassistant.common.error.ResourceNotFoundException.class)
                .hasMessage("ONTOLOGY_REFERENCE_PACKAGE_NOT_FOUND");
    }
}
