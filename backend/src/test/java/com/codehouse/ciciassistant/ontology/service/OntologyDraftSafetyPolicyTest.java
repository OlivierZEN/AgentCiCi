package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OntologyDraftSafetyPolicyTest {

    private final OntologyDraftSafetyPolicy policy = new OntologyDraftSafetyPolicy(
            new ObjectMapper().findAndRegisterModules());

    @Test
    void acceptsABoundedDomainNeutralDocumentAndMappingBatch() {
        OntologyDocument document = document(
                "bounded",
                List.of(concept("entity", List.of(property("name", OntologyDocument.DataType.TEXT,
                        List.of())))),
                List.of(), List.of(), List.of(), List.of(), List.of(mapping()));

        assertThatCode(() -> policy.validateDocument(document)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validateMappings(document.mappings())).doesNotThrowAnyException();
    }

    @Test
    void validatesWorkspaceMetadataWithTheSameStableBoundedContract() throws Exception {
        assertThatCode(() -> policy.validateWorkspaceMetadata(
                "project-delivery", "项目交付", "领域中立工作区"))
                .doesNotThrowAnyException();

        assertInvalid(() -> policy.validateWorkspaceMetadata(null, "项目交付", null));
        assertInvalid(() -> policy.validateWorkspaceMetadata("project-delivery", null, null));
        assertInvalid(() -> policy.validateWorkspaceMetadata(
                "project-delivery", "项目交付", "x".repeat(65_537)));

        ObjectMapper oversizedUtf8Mapper = mock(ObjectMapper.class);
        when(oversizedUtf8Mapper.writeValueAsString(any()))
                .thenReturn("界".repeat(OntologyDraftSafetyPolicy.MAX_DOCUMENT_BYTES / 3 + 1));
        OntologyDraftSafetyPolicy utf8Policy = new OntologyDraftSafetyPolicy(oversizedUtf8Mapper);
        assertInvalid(() -> utf8Policy.validateWorkspaceMetadata(
                "project-delivery", "项目交付", "领域中立工作区"));
    }

    @Test
    void rejectsNullRequiredListsEnumsAndEnumValuesWithStableContract() {
        OntologyDocument nullConcepts = document(
                "null-lists", null, List.of(), List.of(), List.of(), List.of(), List.of());
        OntologyDocument nullProperties = document(
                "null-properties",
                List.of(concept("entity", null)),
                List.of(), List.of(), List.of(), List.of(), List.of());
        OntologyDocument nullEnumValues = document(
                "null-enum",
                List.of(concept("entity", List.of(property(
                        "status", OntologyDocument.DataType.ENUM, null)))),
                List.of(), List.of(), List.of(), List.of(), List.of());
        OntologyDocument duplicateEnumValues = document(
                "duplicate-enum",
                List.of(concept("entity", List.of(property(
                        "status", OntologyDocument.DataType.ENUM, List.of("OPEN", " OPEN "))))),
                List.of(), List.of(), List.of(), List.of(), List.of());

        for (OntologyDocument invalid : List.of(
                nullConcepts, nullProperties, nullEnumValues, duplicateEnumValues)) {
            assertInvalid(() -> policy.validateDocument(invalid));
        }
    }

    @Test
    void rejectsCountByteAndDatabaseStringLimits() {
        List<OntologyDocument.Concept> tooManyConcepts = IntStream.range(0, 101)
                .mapToObj(index -> concept("concept-" + index, List.of()))
                .toList();
        List<OntologyDocument.Property> tooManyProperties = IntStream.range(0, 101)
                .mapToObj(index -> property(
                        "property-" + index, OntologyDocument.DataType.TEXT, List.of()))
                .toList();
        OntologyDocument oversizedJson = document(
                "oversized-json",
                List.of(concept("entity", List.of(new OntologyDocument.Property(
                        "description", "描述", "x".repeat(1_048_576),
                        OntologyDocument.DataType.TEXT, false, false, false, true, List.of())))),
                List.of(), List.of(), List.of(), List.of(), List.of());
        OntologyDocument overlongKey = document(
                "overlong-key",
                List.of(concept("x".repeat(129), List.of())),
                List.of(), List.of(), List.of(), List.of(), List.of());

        for (OntologyDocument invalid : List.of(
                document("too-many-concepts", tooManyConcepts,
                        List.of(), List.of(), List.of(), List.of(), List.of()),
                document("too-many-properties", List.of(concept("entity", tooManyProperties)),
                        List.of(), List.of(), List.of(), List.of(), List.of()),
                oversizedJson,
                overlongKey)) {
            assertInvalid(() -> policy.validateDocument(invalid));
        }
    }

    @Test
    void rejectsNonFinitePositionsAndMappingConfidence() {
        OntologyDocument.Concept infinite = new OntologyDocument.Concept(
                "entity", "实体", "实体", null, OntologyDocument.ConceptType.ENTITY,
                null, Double.POSITIVE_INFINITY, 0, true, true, List.of());
        OntologyDocument.Mapping nanConfidence = new OntologyDocument.Mapping(
                "PROPERTY", "entity.name", 1L, "entities", "name", null,
                "DIRECT", Double.NaN, "MANUAL", "PENDING");

        assertInvalid(() -> policy.validateDocument(document(
                "infinite-position", List.of(infinite), List.of(), List.of(), List.of(),
                List.of(), List.of())));
        assertInvalid(() -> policy.validateMappings(List.of(nanConfidence)));
    }

    @Test
    void rejectsDeepOrOversizedMetricFilterValues() {
        Object nested = "leaf";
        for (int depth = 0; depth < 10; depth++) {
            nested = Map.of("level", nested);
        }
        OntologyDocument.Metric deepFilter = metric(new OntologyDocument.QueryFilter(
                "name", OntologyDocument.Operator.EQ, nested));
        OntologyDocument.Metric oversizedList = metric(new OntologyDocument.QueryFilter(
                "name", OntologyDocument.Operator.IN,
                IntStream.range(0, 101).boxed().toList()));

        assertInvalid(() -> policy.validateDocument(document(
                "deep-filter", List.of(concept("entity", List.of(property(
                        "name", OntologyDocument.DataType.TEXT, List.of())))),
                List.of(), List.of(deepFilter), List.of(), List.of(), List.of())));
        assertInvalid(() -> policy.validateDocument(document(
                "wide-filter", List.of(concept("entity", List.of(property(
                        "name", OntologyDocument.DataType.TEXT, List.of())))),
                List.of(), List.of(oversizedList), List.of(), List.of(), List.of())));
    }

    @Test
    void appliesTheMappingBudgetIndependentlyForReplaceOperations() {
        List<OntologyDocument.Mapping> oversized = new ArrayList<>();
        for (int index = 0; index < 5_001; index++) {
            oversized.add(new OntologyDocument.Mapping(
                    "PROPERTY", "entity.field-" + index, 1L,
                    "entities", "field-" + index, null,
                    "DIRECT", 1, "MANUAL", "PENDING"));
        }

        assertInvalid(() -> policy.validateMappings(oversized));
        assertInvalid(() -> policy.validateMappings(null));
    }

    private OntologyDocument document(
            String key,
            List<OntologyDocument.Concept> concepts,
            List<OntologyDocument.Relation> relations,
            List<OntologyDocument.Metric> metrics,
            List<OntologyDocument.Action> actions,
            List<OntologyDocument.DataSource> sources,
            List<OntologyDocument.Mapping> mappings) {
        return new OntologyDocument(
                key, "本体", "领域中立结构安全测试", concepts, relations, metrics,
                actions, sources, mappings);
    }

    private OntologyDocument.Concept concept(
            String key,
            List<OntologyDocument.Property> properties) {
        return new OntologyDocument.Concept(
                key, "实体", "实体", null, OntologyDocument.ConceptType.ENTITY,
                null, 0, 0, true, true, properties);
    }

    private OntologyDocument.Property property(
            String key,
            OntologyDocument.DataType dataType,
            List<String> enumValues) {
        return new OntologyDocument.Property(
                key, "字段", null, dataType,
                false, false, false, true, enumValues);
    }

    private OntologyDocument.Metric metric(OntologyDocument.QueryFilter filter) {
        return new OntologyDocument.Metric(
                "metric", "指标", "entity", OntologyDocument.Aggregation.COUNT,
                null, List.of(), null, List.of(filter));
    }

    private OntologyDocument.Mapping mapping() {
        return new OntologyDocument.Mapping(
                "PROPERTY", "entity.name", 1L, "entities", "name", null,
                "DIRECT", 1, "MANUAL", "PENDING");
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ONTOLOGY_VALIDATION_FAILED");
    }
}
