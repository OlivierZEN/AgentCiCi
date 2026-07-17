package com.codehouse.ciciassistant.ontology.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.AdapterContext;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.DataSourceConfig;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalFilter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalQuery;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InlineSampleOntologyAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InlineSampleOntologyAdapter adapter =
            new InlineSampleOntologyAdapter(objectMapper);

    @Test
    void rejectsObjectsAboveTheBoundedFieldBudgetBeforeExecution() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < 129; index++) {
            row.put("field_" + index, index);
        }
        String json = objectMapper.writeValueAsString(Map.of("items", List.of(row)));

        assertThatThrownBy(() -> adapter.executeRead(
                context(),
                source(json),
                new PhysicalQuery(
                        "items", List.of("field_0"), List.of(), List.of(), 50)))
                .hasMessageContaining("INLINE_SAMPLE_FIELD_LIMIT_EXCEEDED");
    }

    @Test
    void appliesTheWhitelistedOperatorsInMemory() {
        String json = """
                {"tasks":[
                  {"name":"设计","status":"ACTIVE","progress":60},
                  {"name":"验收","status":"DONE","progress":100},
                  {"name":"规划","status":"PLANNED","progress":10}
                ]}
                """;

        OntologyDataSourceAdapter.PhysicalResult result = adapter.executeRead(
                context(),
                source(json),
                new PhysicalQuery(
                        "tasks",
                        List.of("name", "progress"),
                        List.of(
                                new PhysicalFilter(
                                        "status",
                                        OntologyDocument.Operator.IN,
                                        List.of("ACTIVE", "DONE")),
                                new PhysicalFilter(
                                        "progress",
                                        OntologyDocument.Operator.BETWEEN,
                                        List.of(50, 80))),
                        List.of(),
                        50));

        assertThat(result.rows()).containsExactly(
                Map.of("name", "设计", "progress", 60));
    }

    @Test
    void rejectsUnknownFieldsAndNonArrayObjects() {
        assertThatThrownBy(() -> adapter.executeRead(
                context(),
                source("{\"tasks\":[{\"name\":\"设计\"}]}"),
                new PhysicalQuery(
                        "tasks", List.of("secret"), List.of(), List.of(), 50)))
                .hasMessageContaining("PHYSICAL_FIELD_NOT_ALLOWED");
        assertThatThrownBy(() -> adapter.discoverObjects(
                context(), source("{\"tasks\":{\"name\":\"设计\"}}")))
                .hasMessageContaining("INLINE_SAMPLE_OBJECT_OF_ARRAYS_REQUIRED");
    }

    private AdapterContext context() {
        return new AdapterContext("org-a", "user-a");
    }

    private DataSourceConfig source(String json) {
        return new DataSourceConfig(
                7L,
                41L,
                "inline-source",
                "内置样例",
                OntologyDocument.SourceType.INLINE_SAMPLE,
                null,
                json,
                null);
    }
}
