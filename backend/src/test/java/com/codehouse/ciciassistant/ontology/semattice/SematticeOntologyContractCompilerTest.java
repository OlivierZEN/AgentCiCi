package com.codehouse.ciciassistant.ontology.semattice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class SematticeOntologyContractCompilerTest {

    private final SematticeOntologyContractCompiler compiler =
            new SematticeOntologyContractCompiler(new ObjectMapper());

    @Test
    void compilesStableObjectsFieldsRelationsAndSemanticAnnotations() {
        var first = compiler.compile(7L, 12L, document());
        var second = compiler.compile(7L, 12L, document());

        assertThat(first).isEqualTo(second);
        assertThat(first.sourceDigest()).hasSize(64);
        assertThat(first.objects()).extracting(
                SematticeOntologyContractCompiler.ObjectDefinition::apiName)
                .containsExactly("project", "task");
        assertThat(first.fields()).extracting(
                SematticeOntologyContractCompiler.FieldDefinition::elementKey,
                SematticeOntologyContractCompiler.FieldDefinition::dataType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("project.name", "text"),
                        org.assertj.core.groups.Tuple.tuple("task.progress", "number"));
        assertThat(first.relations()).singleElement().satisfies(relation -> {
            assertThat(relation.apiName()).isEqualTo("contains_task");
            assertThat(relation.relationType()).isEqualTo("lookup");
        });
        assertThat(first.objects().getFirst().semantic())
                .containsEntry("schema", "agentcici.ontology.semantic/v1")
                .containsEntry("workspace_id", "7")
                .containsEntry("source_revision", 12L);
    }

    @Test
    void rejectsApiNameCollisionsInsteadOfSilentlyMergingElements() {
        OntologyDocument base = document();
        OntologyDocument.Concept duplicate = new OntologyDocument.Concept(
                "sales-order", "销售订单", "销售订单", "", OntologyDocument.ConceptType.ENTITY,
                "name", 0, 0, true, true, List.of());
        OntologyDocument.Concept collision = new OntologyDocument.Concept(
                "sales_order", "销售订单二", "销售订单二", "", OntologyDocument.ConceptType.ENTITY,
                "name", 0, 0, true, true, List.of());
        OntologyDocument value = new OntologyDocument(
                base.key(), base.name(), base.description(), List.of(duplicate, collision),
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> compiler.compile(7L, 1L, value))
                .hasMessage("SEMATTICE_ONTOLOGY_OBJECT_API_CONFLICT");
    }

    private OntologyDocument document() {
        OntologyDocument.Concept project = new OntologyDocument.Concept(
                "project", "项目", "项目", "交付项目", OntologyDocument.ConceptType.ENTITY,
                "name", 0, 0, true, true, List.of(new OntologyDocument.Property(
                        "name", "项目名称", "项目的业务名称", OntologyDocument.DataType.TEXT,
                        true, false, false, true, List.of())));
        OntologyDocument.Concept task = new OntologyDocument.Concept(
                "task", "任务", "任务", "项目任务", OntologyDocument.ConceptType.EVENT,
                "progress", 0, 0, true, true, List.of(new OntologyDocument.Property(
                        "progress", "进度", "完成百分比", OntologyDocument.DataType.DECIMAL,
                        false, false, false, true, List.of())));
        OntologyDocument.Relation relation = new OntologyDocument.Relation(
                "contains-task", "包含任务", "项目包含任务", "project", "task",
                OntologyDocument.Cardinality.ONE_TO_MANY, "包含", "属于", true, true);
        return new OntologyDocument(
                "project-delivery", "项目交付", "统一交付语义",
                List.of(task, project), List.of(relation), List.of(), List.of(), List.of(), List.of());
    }
}
