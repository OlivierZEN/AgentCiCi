package com.codehouse.ciciassistant.ontology.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.AdapterContext;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.DataSourceConfig;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalFilter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalQuery;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SematticeOntologyAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SematticeOntologyGateway gateway = mock(SematticeOntologyGateway.class);
    private final SematticeOntologyAdapter adapter =
            new SematticeOntologyAdapter(gateway, objectMapper);
    private final AdapterContext context = new AdapterContext("company-a", "user-a");
    private final DataSourceConfig source = new DataSourceConfig(
            9L, 7L, "semattice", "Semattice", OntologyDocument.SourceType.CONNECTOR,
            "semattice", "{\"adapterKey\":\"semattice\"}", null);

    @BeforeEach
    void metadata() throws Exception {
        when(gateway.invokeRead(
                "company-a", "user-a", "metadata.version.get-current", Map.of()))
                .thenReturn(objectMapper.readTree("""
                        {
                          "version":{"metadata_version_id":"01900000-0000-7000-8000-000000000001","sequence":4,"status":"published"},
                          "objects":[{"object_id":"01900000-0000-7000-8000-000000000010","api_name":"project","label":"项目","description":"交付项目","semantic":{"domain":"delivery"}}],
                          "fields":[
                            {"field_id":"01900000-0000-7000-8000-000000000020","object_id":"01900000-0000-7000-8000-000000000010","api_name":"name","label":"项目名称","data_type":"text","required":true,"indexed":true,"unique_value":false,"lifecycle_state":"active","semantic":{}},
                            {"field_id":"01900000-0000-7000-8000-000000000021","object_id":"01900000-0000-7000-8000-000000000010","api_name":"archived","label":"旧字段","data_type":"text","required":false,"indexed":false,"unique_value":false,"lifecycle_state":"tombstone","semantic":{}}
                          ],
                          "relations":[]
                        }
                        """));
    }

    @Test
    void discoversPublishedObjectsAndFieldsWithStableMetadataIdentity() {
        var objects = adapter.discoverObjects(context, source);
        var fields = adapter.discoverFields(context, source, "project");

        assertThat(objects).hasSize(1);
        assertThat(objects.getFirst().key()).isEqualTo("project");
        assertThat(objects.getFirst().metadataJson()).contains(
                "01900000-0000-7000-8000-000000000010", "metadataVersionId");
        assertThat(fields).hasSize(1);
        assertThat(fields.getFirst().key()).isEqualTo("name");
        assertThat(fields.getFirst().nullable()).isFalse();
        assertThat(fields.getFirst().metadataJson()).contains(
                "01900000-0000-7000-8000-000000000020", "indexed");
    }

    @Test
    void executesBoundedSingleObjectQueryAndProjectsOnlyRequestedFields() throws Exception {
        JsonNode queryResult = objectMapper.readTree("""
                {
                  "records":[{"record_id":"01900000-0000-7000-8000-000000000100","data":{"name":"星轨","secret":"hidden"}}],
                  "next_cursor":"01900000-0000-7000-8000-000000000100",
                  "plan":{"strategy":"typed_index","indexed_fields":["name"],"limit":25}
                }
                """);
        when(gateway.invokeRead(eq("company-a"), eq("user-a"),
                eq("runtime.record.query"), anyMap())).thenReturn(queryResult);

        var result = adapter.executeRead(context, source, new PhysicalQuery(
                "project",
                List.of("name"),
                List.of(new PhysicalFilter("name", OntologyDocument.Operator.EQ, "星轨")),
                List.of(),
                25));

        assertThat(result.rows()).containsExactly(Map.of("name", "星轨"));
        assertThat(result.moreAvailable()).isTrue();
        ArgumentCaptor<Map<String, Object>> input = ArgumentCaptor.forClass(Map.class);
        verify(gateway).invokeRead(eq("company-a"), eq("user-a"),
                eq("runtime.record.query"), input.capture());
        assertThat(input.getValue()).containsEntry("object_api_name", "project");
        assertThat(String.valueOf(input.getValue().get("filters")))
                .contains("op=eq", "field=name", "value=星轨");
    }

    @Test
    void rejectsQueryFeaturesThatSematticeCannotExecuteFaithfully() {
        assertThatThrownBy(() -> adapter.validateQuery(source, new PhysicalQuery(
                "project", List.of("name"),
                List.of(new PhysicalFilter(
                        "name", OntologyDocument.Operator.CONTAINS, "轨")),
                List.of(), 25)))
                .hasMessage("QUERY_OPERATOR_NOT_SUPPORTED");

        assertThatThrownBy(() -> adapter.validateQuery(source, new PhysicalQuery(
                "project", List.of("name"), List.of(),
                List.of(new OntologyDataSourceAdapter.PhysicalOrder(
                        "name", OntologyDataSourceAdapter.Direction.ASC)), 25)))
                .hasMessage("QUERY_ORDER_NOT_SUPPORTED");
    }

    @Test
    void acceptsOnlyMinimalPublicSematticeConnectorConfiguration() throws Exception {
        adapter.validatePublicConfig(objectMapper.readTree("{\"adapterKey\":\"semattice\"}"));

        assertThatThrownBy(() -> adapter.validatePublicConfig(objectMapper.readTree(
                "{\"adapterKey\":\"semattice\",\"tenantId\":\"attacker\"}")))
                .hasMessage("DATA_SOURCE_CONFIG_INVALID");
    }
}
