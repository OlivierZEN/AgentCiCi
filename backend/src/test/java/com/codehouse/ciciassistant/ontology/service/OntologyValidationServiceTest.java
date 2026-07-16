package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

class OntologyValidationServiceTest {

    private final OntologyValidationService validator = new OntologyValidationService();

    @Test
    void rejectsDanglingRelationAndDuplicatePropertyKeys() {
        OntologyDocument invalid = document(
                List.of(concept(
                        "project",
                        "name",
                        true,
                        property("name", OntologyDocument.DataType.TEXT, false, true),
                        property("name", OntologyDocument.DataType.TEXT, false, true))),
                List.of(new OntologyDocument.Relation(
                        "contains-task",
                        "包含任务",
                        "",
                        "project",
                        "missing-task",
                        OntologyDocument.Cardinality.ONE_TO_MANY,
                        "包含",
                        "属于",
                        true,
                        true)),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        List<OntologyValidationService.ValidationIssue> issues = validator.validate(invalid, true);

        assertThat(issues)
                .extracting(OntologyValidationService.ValidationIssue::code)
                .contains("DUPLICATE_PROPERTY_KEY", "RELATION_TARGET_NOT_FOUND");
    }

    @Test
    void validatesKeysReferencesMetricsActionsMappingsAndSensitiveQueryRules() {
        OntologyDocument invalid = document(
                List.of(concept(
                        "Project Bad",
                        "missing-display",
                        true,
                        property("amount", OntologyDocument.DataType.DECIMAL, false, true),
                        property("secret", OntologyDocument.DataType.TEXT, true, true))),
                List.of(),
                List.of(new OntologyDocument.Metric(
                        "total-amount",
                        "金额",
                        "Project Bad",
                        OntologyDocument.Aggregation.SUM,
                        "missing-measure",
                        List.of("missing-group"),
                        "amount",
                        List.of(new OntologyDocument.QueryFilter(
                                "missing-filter", OntologyDocument.Operator.EQ, "x")))),
                List.of(new OntologyDocument.Action(
                        "assign-owner",
                        "分配负责人",
                        "missing-concept",
                        "",
                        List.of(
                                new OntologyDocument.ActionParameter(
                                        "owner", "负责人", OntologyDocument.DataType.REFERENCE, true),
                                new OntologyDocument.ActionParameter(
                                        "owner", "负责人", OntologyDocument.DataType.REFERENCE, true)))),
                List.of(new OntologyDocument.DataSource(
                        7L,
                        "delivery-source",
                        "交付数据",
                        OntologyDocument.SourceType.CONNECTOR,
                        "{}")),
                List.of(new OntologyDocument.Mapping(
                        "PROPERTY",
                        "Project Bad.missing",
                        7L,
                        "projects",
                        "missing",
                        null,
                        "javascript: value.trim()",
                        0.9,
                        "MANUAL",
                        "VALID")));

        List<OntologyValidationService.ValidationIssue> issues = validator.validate(invalid, true);

        assertThat(issues)
                .extracting(OntologyValidationService.ValidationIssue::code)
                .contains(
                        "INVALID_KEY",
                        "DISPLAY_PROPERTY_NOT_FOUND",
                        "SENSITIVE_PROPERTY_QUERYABLE",
                        "METRIC_MEASURE_NOT_FOUND",
                        "METRIC_GROUP_BY_NOT_FOUND",
                        "METRIC_TIME_PROPERTY_INVALID",
                        "METRIC_FILTER_PROPERTY_NOT_FOUND",
                        "ACTION_CONCEPT_NOT_FOUND",
                        "DUPLICATE_ACTION_PARAMETER_KEY",
                        "MAPPING_TARGET_NOT_FOUND",
                        "MAPPING_TRANSFORM_NOT_ALLOWED");
        assertThat(issues).isSortedAccordingTo(OntologyValidationService.ValidationIssue.ORDERING);
    }

    @Test
    void onlyRequiresMappingsForQueryableTargetsAtPublishTime() {
        OntologyDocument unmapped = document(
                List.of(concept(
                        "project",
                        "name",
                        true,
                        property("name", OntologyDocument.DataType.TEXT, false, true))),
                List.of(),
                List.of(),
                List.of(),
                List.of(new OntologyDocument.DataSource(
                        1L,
                        "delivery-source",
                        "交付数据",
                        OntologyDocument.SourceType.CONNECTOR,
                        "{}")),
                List.of());

        assertThat(validator.validate(unmapped, false))
                .extracting(OntologyValidationService.ValidationIssue::code)
                .doesNotContain("QUERYABLE_MAPPING_REQUIRED");
        assertThat(validator.validate(unmapped, true))
                .extracting(OntologyValidationService.ValidationIssue::code)
                .contains("QUERYABLE_MAPPING_REQUIRED");
    }

    @Test
    void rejectsGraphqlNameCollisionsAndEmptyOutputTypes() {
        OntologyDocument colliding = document(
                List.of(
                        concept(
                                "sales-order",
                                "display-name",
                                true,
                                property("display-name", OntologyDocument.DataType.TEXT, false, true)),
                        concept(
                                "sales_order",
                                "display_name",
                                true,
                                property("display_name", OntologyDocument.DataType.TEXT, false, true))),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        OntologyDocument empty = document(
                List.of(concept("empty-concept", null, true)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertThat(validator.validate(colliding, false))
                .extracting(OntologyValidationService.ValidationIssue::code)
                .contains("GRAPHQL_NAME_COLLISION");
        assertThat(validator.validate(empty, false))
                .extracting(OntologyValidationService.ValidationIssue::code)
                .contains("GRAPHQL_OBJECT_EMPTY");

        OntologyDocument noQueries = document(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(validator.validate(noQueries, false))
                .extracting(OntologyValidationService.ValidationIssue::code)
                .contains("GRAPHQL_QUERY_EMPTY");

        OntologyDocument reservedQueryType = document(
                List.of(concept(
                        "query",
                        "name",
                        true,
                        property("name", OntologyDocument.DataType.TEXT, false, true))),
                List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(validator.validate(reservedQueryType, false))
                .extracting(OntologyValidationService.ValidationIssue::code)
                .contains("GRAPHQL_NAME_COLLISION");
    }

    @Test
    void rejectsMetricsThatBypassConceptOrPropertyQuerySafety() {
        OntologyDocument invalidMetrics = document(
                List.of(
                        concept(
                                "project",
                                "name",
                                true,
                                property("name", OntologyDocument.DataType.TEXT, false, true),
                                property("secret_amount", OntologyDocument.DataType.DECIMAL, true, false)),
                        concept(
                                "archived",
                                "name",
                                false,
                                property("name", OntologyDocument.DataType.TEXT, false, true))),
                List.of(),
                List.of(
                        new OntologyDocument.Metric(
                                "secret-total",
                                "敏感总额",
                                "project",
                                OntologyDocument.Aggregation.SUM,
                                "secret_amount",
                                List.of(),
                                null,
                                List.of()),
                        new OntologyDocument.Metric(
                                "archived-count",
                                "归档数量",
                                "archived",
                                OntologyDocument.Aggregation.COUNT,
                                null,
                                List.of(),
                                null,
                                List.of())),
                List.of(),
                List.of(),
                List.of());

        assertThat(validator.validate(invalidMetrics, false))
                .extracting(OntologyValidationService.ValidationIssue::code)
                .contains("METRIC_PROPERTY_NOT_QUERYABLE", "METRIC_CONCEPT_NOT_QUERYABLE");
    }

    @Test
    void rejectsReservedGeneratedAndIntrospectionGraphqlNames() {
        List<OntologyDocument.Concept> concepts = List.of(
                queryableConcept("string"),
                queryableConcept("int"),
                queryableConcept("float"),
                queryableConcept("boolean"),
                queryableConcept("i-d"),
                queryableConcept("query"),
                queryableConcept("mutation"),
                queryableConcept("subscription"),
                queryableConcept("semantic-operator"),
                queryableConcept("sort-direction"),
                queryableConcept("project"),
                queryableConcept("project-filter"),
                queryableConcept("project-order"),
                queryableConcept("__introspection"));

        assertThat(validator.validate(
                        document(concepts, List.of(), List.of(), List.of(), List.of(), List.of()),
                        false))
                .filteredOn(issue -> "GRAPHQL_NAME_COLLISION".equals(issue.code()))
                .hasSizeGreaterThanOrEqualTo(13)
                .allMatch(issue -> issue.severity() == OntologyValidationService.Severity.ERROR);
    }

    @Test
    void rejectsBlankAndDuplicateEnumValuesAfterTrimming() {
        OntologyDocument.Property status = new OntologyDocument.Property(
                "status",
                "状态",
                "",
                OntologyDocument.DataType.ENUM,
                true,
                false,
                false,
                true,
                List.of("ACTIVE", " ", "ACTIVE", " DONE ", "DONE"));
        OntologyDocument invalid = document(
                List.of(concept("project", "status", true, status)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertThat(validator.validate(invalid, false))
                .extracting(OntologyValidationService.ValidationIssue::code)
                .contains("ENUM_VALUE_BLANK", "DUPLICATE_ENUM_VALUE");
    }

    @Test
    void rejectsQueryableRelationEndpointsAndIncompleteRelationMappings() {
        OntologyDocument invalid = document(
                List.of(
                        conceptWithState("project", true, true),
                        conceptWithState("task", false, false),
                        conceptWithState("archive", false, false)),
                List.of(
                        relation("contains-task", "project", "task"),
                        relation("archive-project", "archive", "project")),
                List.of(),
                List.of(),
                List.of(new OntologyDocument.DataSource(
                        7L,
                        "delivery-source",
                        "交付数据",
                        OntologyDocument.SourceType.CONNECTOR,
                        "{}")),
                List.of(
                        new OntologyDocument.Mapping(
                                "RELATION",
                                "contains-task",
                                7L,
                                "projects",
                                null,
                                "project_id",
                                "DIRECT",
                                1.0,
                                "MANUAL",
                                "VALID"),
                        new OntologyDocument.Mapping(
                                "RELATION",
                                "archive-project",
                                7L,
                                "archives",
                                "project_id",
                                null,
                                "DIRECT",
                                1.0,
                                "MANUAL",
                                "VALID")));

        assertThat(validator.validate(invalid, false))
                .extracting(OntologyValidationService.ValidationIssue::code)
                .contains(
                        "RELATION_TARGET_NOT_QUERYABLE",
                        "RELATION_SOURCE_NOT_QUERYABLE",
                        "MAPPING_PHYSICAL_FIELD_REQUIRED",
                        "MAPPING_RELATION_TARGET_FIELD_REQUIRED");
    }

    private static OntologyDocument document(
            List<OntologyDocument.Concept> concepts,
            List<OntologyDocument.Relation> relations,
            List<OntologyDocument.Metric> metrics,
            List<OntologyDocument.Action> actions,
            List<OntologyDocument.DataSource> dataSources,
            List<OntologyDocument.Mapping> mappings) {
        return new OntologyDocument(
                "project-delivery",
                "项目交付",
                "通用本体样例",
                concepts,
                relations,
                metrics,
                actions,
                dataSources,
                mappings);
    }

    private static OntologyDocument.Concept concept(
            String key,
            String displayPropertyKey,
            boolean queryable,
            OntologyDocument.Property... properties) {
        return new OntologyDocument.Concept(
                key,
                "项目",
                "项目",
                "",
                OntologyDocument.ConceptType.ENTITY,
                displayPropertyKey,
                0,
                0,
                queryable,
                true,
                List.of(properties));
    }

    private static OntologyDocument.Concept queryableConcept(String key) {
        return concept(
                key,
                "value",
                true,
                property("value", OntologyDocument.DataType.TEXT, false, true));
    }

    private static OntologyDocument.Concept conceptWithState(
            String key,
            boolean queryable,
            boolean enabled) {
        return new OntologyDocument.Concept(
                key,
                key,
                key,
                "",
                OntologyDocument.ConceptType.ENTITY,
                "name",
                0,
                0,
                queryable,
                enabled,
                List.of(property("name", OntologyDocument.DataType.TEXT, false, true)));
    }

    private static OntologyDocument.Relation relation(
            String key,
            String sourceConceptKey,
            String targetConceptKey) {
        return new OntologyDocument.Relation(
                key,
                key,
                "",
                sourceConceptKey,
                targetConceptKey,
                OntologyDocument.Cardinality.MANY_TO_ONE,
                key,
                key,
                true,
                true);
    }

    private static OntologyDocument.Property property(
            String key,
            OntologyDocument.DataType dataType,
            boolean sensitive,
            boolean queryable) {
        return new OntologyDocument.Property(
                key,
                key,
                "",
                dataType,
                false,
                false,
                sensitive,
                queryable,
                List.of());
    }
}
