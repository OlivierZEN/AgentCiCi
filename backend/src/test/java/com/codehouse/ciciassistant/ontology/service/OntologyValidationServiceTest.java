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
