package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class OntologyCompilerServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OntologyCompilerService compiler = new OntologyCompilerService(objectMapper);

    @Test
    void compilesStableContractsFromSameDocument() throws Exception {
        OntologyCompilerService.CompiledContracts first =
                compiler.compile(projectDeliveryDocument(), 1);
        OntologyCompilerService.CompiledContracts second =
                compiler.compile(projectDeliveryDocument(), 1);

        assertThat(first.contentHash()).isEqualTo(second.contentHash());
        assertThat(first.graphqlSdl()).isEqualTo(expectedGraphqlSdl());
        assertThat(first.graphqlSdl()).doesNotContain("type Mutation", "publish");
        assertThat(first.jsonSchema())
                .contains("https://json-schema.org/draft/2020-12/schema");
        JsonNode jsonSchema = objectMapper.readTree(first.jsonSchema());
        JsonNode relationSchema = jsonSchema
                .path("$defs")
                .path("Project")
                .path("properties")
                .path("contains-task");
        assertThat(relationSchema.path("type").asText()).isEqualTo("array");
        assertThat(relationSchema.path("items").path("$ref").asText())
                .isEqualTo("#/$defs/Task");
        JsonNode queryContract = objectMapper.readTree(first.queryContractJson());
        assertThat(queryContract.path("version").asInt()).isEqualTo(1);
        assertThat(queryContract.path("concepts").isArray()).isTrue();
        assertThat(queryContract.path("relations").path(0).path("key").asText())
                .isEqualTo("contains-task");
    }

    @Test
    void canonicalizesCollectionOrderBeforeHashingAndSerialization() {
        OntologyDocument original = projectDeliveryDocument();
        OntologyDocument reordered = reverseCollections(original);

        OntologyCompilerService.CompiledContracts first = compiler.compile(original, 1);
        OntologyCompilerService.CompiledContracts second = compiler.compile(reordered, 1);

        assertThat(second.snapshotJson()).isEqualTo(first.snapshotJson());
        assertThat(second.jsonSchema()).isEqualTo(first.jsonSchema());
        assertThat(second.graphqlSdl()).isEqualTo(first.graphqlSdl());
        assertThat(second.queryContractJson()).isEqualTo(first.queryContractJson());
        assertThat(second.contentHash()).isEqualTo(first.contentHash());
    }

    @Test
    void includesVersionInContentHash() {
        assertThat(compiler.compile(projectDeliveryDocument(), 1).contentHash())
                .isNotEqualTo(compiler.compile(projectDeliveryDocument(), 2).contentHash());
    }

    @Test
    void omitsRelationsUnlessRelationAndBothEndpointsAreEnabledAndQueryable() throws Exception {
        OntologyDocument.Concept project = concept(
                "project",
                "项目",
                "name",
                OntologyDocument.ConceptType.ENTITY,
                List.of(property("name", OntologyDocument.DataType.TEXT, true, List.of())));
        OntologyDocument.Concept task = concept(
                "task",
                "任务",
                "name",
                OntologyDocument.ConceptType.ENTITY,
                List.of(property("name", OntologyDocument.DataType.TEXT, true, List.of())));
        OntologyDocument.Concept archived = new OntologyDocument.Concept(
                "archived",
                "归档",
                "归档",
                "",
                OntologyDocument.ConceptType.ENTITY,
                "name",
                0,
                0,
                true,
                false,
                List.of(property("name", OntologyDocument.DataType.TEXT, true, List.of())));
        OntologyDocument document = new OntologyDocument(
                "relation-visibility",
                "关系可见性",
                "",
                List.of(project, task, archived),
                List.of(
                        relation("hidden-task", "project", "task", false, true),
                        relation("disabled-task", "project", "task", true, false),
                        relation("archived-task", "project", "archived", true, true)),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        OntologyCompilerService.CompiledContracts contracts = compiler.compile(document, 1);

        assertThat(contracts.graphqlSdl())
                .doesNotContain("hiddenTask", "disabledTask", "archivedTask");
        JsonNode schema = objectMapper.readTree(contracts.jsonSchema());
        JsonNode schemaProperties = schema.path("$defs").path("Project").path("properties");
        assertThat(schemaProperties.has("hidden-task")).isFalse();
        assertThat(schemaProperties.has("disabled-task")).isFalse();
        assertThat(schemaProperties.has("archived-task")).isFalse();
        assertThat(objectMapper.readTree(contracts.queryContractJson()).path("relations"))
                .isEmpty();
    }

    @Test
    void compilesSingleCardinalityRelationAsDirectReference() throws Exception {
        OntologyDocument.Concept project = concept(
                "project",
                "项目",
                "name",
                OntologyDocument.ConceptType.ENTITY,
                List.of(property("name", OntologyDocument.DataType.TEXT, true, List.of())));
        OntologyDocument.Concept task = concept(
                "task",
                "任务",
                "name",
                OntologyDocument.ConceptType.ENTITY,
                List.of(property("name", OntologyDocument.DataType.TEXT, true, List.of())));
        OntologyDocument.Relation ownerTask = new OntologyDocument.Relation(
                "owner-task",
                "负责任务",
                "",
                "project",
                "task",
                OntologyDocument.Cardinality.MANY_TO_ONE,
                "负责",
                "所属",
                true,
                true);
        OntologyDocument document = new OntologyDocument(
                "single-relation",
                "单值关系",
                "",
                List.of(project, task),
                List.of(ownerTask),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        OntologyCompilerService.CompiledContracts contracts = compiler.compile(document, 1);

        JsonNode relationSchema = objectMapper.readTree(contracts.jsonSchema())
                .path("$defs")
                .path("Project")
                .path("properties")
                .path("owner-task");
        assertThat(relationSchema.path("$ref").asText()).isEqualTo("#/$defs/Task");
        assertThat(relationSchema.has("type")).isFalse();
        assertThat(contracts.graphqlSdl()).contains("  ownerTask: Task\n");
    }

    static OntologyDocument projectDeliveryDocument() {
        OntologyDocument.Property taskStatus = property(
                "status",
                OntologyDocument.DataType.ENUM,
                true,
                List.of("PLANNED", "ACTIVE", "DONE"));
        OntologyDocument.Property projectName = property(
                "name", OntologyDocument.DataType.TEXT, true, List.of());
        OntologyDocument.Property projectBudget = property(
                "budget", OntologyDocument.DataType.DECIMAL, false, List.of());
        OntologyDocument.Concept task = concept(
                "task",
                "任务",
                "status",
                OntologyDocument.ConceptType.EVENT,
                List.of(taskStatus));
        OntologyDocument.Concept project = concept(
                "project",
                "项目",
                "name",
                OntologyDocument.ConceptType.ENTITY,
                List.of(projectName, projectBudget));
        OntologyDocument.Relation containsTask = new OntologyDocument.Relation(
                "contains-task",
                "包含任务",
                "项目包含任务",
                "project",
                "task",
                OntologyDocument.Cardinality.ONE_TO_MANY,
                "包含",
                "属于",
                true,
                true);
        OntologyDocument.Metric totalBudget = new OntologyDocument.Metric(
                "total-budget",
                "预算总额",
                "project",
                OntologyDocument.Aggregation.SUM,
                "budget",
                List.of(),
                null,
                List.of());
        OntologyDocument.Action assignOwner = new OntologyDocument.Action(
                "assign-owner",
                "分配负责人",
                "task",
                "仅编译动作契约，不生成写接口",
                List.of(new OntologyDocument.ActionParameter(
                        "owner", "负责人", OntologyDocument.DataType.REFERENCE, true)));
        OntologyDocument.DataSource source = new OntologyDocument.DataSource(
                1L,
                "delivery-source",
                "交付数据",
                OntologyDocument.SourceType.INLINE_SAMPLE,
                "{}");

        return new OntologyDocument(
                "project-delivery",
                "项目交付",
                "领域无关通用性样例",
                List.of(task, project),
                List.of(containsTask),
                List.of(totalBudget),
                List.of(assignOwner),
                List.of(source),
                List.of(
                        mapping("CONCEPT", "project", 1L, "projects", null),
                        mapping("PROPERTY", "project.name", 1L, "projects", "name"),
                        mapping("PROPERTY", "project.budget", 1L, "projects", "budget"),
                        mapping("CONCEPT", "task", 1L, "tasks", null),
                        mapping("PROPERTY", "task.status", 1L, "tasks", "status"),
                        mapping("RELATION", "contains-task", 1L, "projects", "task_id")));
    }

    private static OntologyDocument reverseCollections(OntologyDocument document) {
        List<OntologyDocument.Concept> concepts = new ArrayList<>();
        for (OntologyDocument.Concept concept : document.concepts()) {
            List<OntologyDocument.Property> properties = reversed(concept.properties());
            concepts.add(new OntologyDocument.Concept(
                    concept.key(),
                    concept.name(),
                    concept.pluralName(),
                    concept.description(),
                    concept.conceptType(),
                    concept.displayPropertyKey(),
                    concept.positionX(),
                    concept.positionY(),
                    concept.queryable(),
                    concept.enabled(),
                    properties));
        }
        Collections.reverse(concepts);
        return new OntologyDocument(
                document.key(),
                document.name(),
                document.description(),
                concepts,
                reversed(document.relations()),
                reversed(document.metrics()),
                reversed(document.actions()),
                reversed(document.dataSources()),
                reversed(document.mappings()));
    }

    private static <T> List<T> reversed(List<T> values) {
        List<T> result = new ArrayList<>(values);
        Collections.reverse(result);
        return result;
    }

    private static OntologyDocument.Concept concept(
            String key,
            String name,
            String displayPropertyKey,
            OntologyDocument.ConceptType type,
            List<OntologyDocument.Property> properties) {
        return new OntologyDocument.Concept(
                key,
                name,
                name,
                "",
                type,
                displayPropertyKey,
                0,
                0,
                true,
                true,
                properties);
    }

    private static OntologyDocument.Property property(
            String key,
            OntologyDocument.DataType type,
            boolean required,
            List<String> enumValues) {
        return new OntologyDocument.Property(
                key,
                key,
                "",
                type,
                required,
                false,
                false,
                true,
                enumValues);
    }

    private static OntologyDocument.Relation relation(
            String key,
            String sourceConceptKey,
            String targetConceptKey,
            boolean queryable,
            boolean enabled) {
        return new OntologyDocument.Relation(
                key,
                key,
                "",
                sourceConceptKey,
                targetConceptKey,
                OntologyDocument.Cardinality.ONE_TO_MANY,
                key,
                key,
                queryable,
                enabled);
    }

    private static OntologyDocument.Mapping mapping(
            String targetType,
            String targetKey,
            Long sourceId,
            String objectKey,
            String fieldKey) {
        return new OntologyDocument.Mapping(
                targetType,
                targetKey,
                sourceId,
                objectKey,
                fieldKey,
                "RELATION".equals(targetType) ? "project_id" : null,
                "DIRECT",
                1.0,
                "MANUAL",
                "VALID");
    }

    private static String expectedGraphqlSdl() {
        return """
                enum SemanticOperator {
                  EQ
                  NE
                  IN
                  CONTAINS
                  GT
                  GTE
                  LT
                  LTE
                  BETWEEN
                  IS_NULL
                }

                enum SortDirection {
                  ASC
                  DESC
                }

                type Project {
                  budget: Float
                  name: String!
                  containsTask: [Task!]!
                }

                input ProjectFilter {
                  field: String!
                  operator: SemanticOperator!
                  value: String
                }

                input ProjectOrder {
                  field: String!
                  direction: SortDirection!
                }

                type Task {
                  status: String!
                }

                input TaskFilter {
                  field: String!
                  operator: SemanticOperator!
                  value: String
                }

                input TaskOrder {
                  field: String!
                  direction: SortDirection!
                }

                type Query {
                  project(id: ID!): Project
                  projectList(filter: ProjectFilter, orderBy: ProjectOrder, limit: Int = 50): [Project!]!
                  task(id: ID!): Task
                  taskList(filter: TaskFilter, orderBy: TaskOrder, limit: Int = 50): [Task!]!
                  totalBudget: Float
                }
                """;
    }
}
