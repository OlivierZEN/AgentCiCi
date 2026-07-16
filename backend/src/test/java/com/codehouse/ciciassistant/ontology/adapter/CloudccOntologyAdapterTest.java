package com.codehouse.ciciassistant.ontology.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.AdapterContext;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.DataSourceConfig;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalFilter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalQuery;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalResult;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalOrder;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.Direction;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CloudccOntologyAdapterTest {

    private final CloudccOpenApiService cloudcc = mock(CloudccOpenApiService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CloudccOntologyAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CloudccOntologyAdapter(cloudcc, objectMapper);
    }

    @Test
    void supportsOnlyTheMatchingPeripheralConnectorKey() {
        assertThat(adapter.supports(source("cloudcc"))).isTrue();
        assertThat(adapter.supports(source("another-connector"))).isFalse();
        assertThat(adapter.supports(new DataSourceConfig(
                7L, 41L, "inline", "内置", OntologyDocument.SourceType.INLINE_SAMPLE,
                "cloudcc", "{}", null))).isFalse();
    }

    @Test
    void discoversObjectsAndFieldsThroughCurrentUserMetadataMethods() {
        when(cloudcc.getStandardObjects("org-a", "user-a")).thenReturn("""
                ✅ 标准对象共 1 个
                ──────────────────────────────
                  客户                    API: Account                    前缀: 001
                """);
        when(cloudcc.getCustomObjects("org-a", "user-a")).thenReturn("""
                ✅ 自定义对象共 1 个
                ──────────────────────────────
                  交付任务                  API: DeliveryTask__c            前缀: a10
                """);
        when(cloudcc.getObjectFields("org-a", "user-a", "001")).thenReturn("""
                ✅ 对象: 客户 (API: Account)
                  标准字段 2 个 | 自定义字段 0 个
                ──────────────────────────────
                【标准字段】
                  记录 ID              API: id                         类型: id
                  客户名称              API: name                       类型: text
                """);

        List<OntologyDataSourceAdapter.PhysicalObject> objects =
                adapter.discoverObjects(new AdapterContext("org-a", "user-a"), source("cloudcc"));
        List<OntologyDataSourceAdapter.PhysicalField> fields =
                adapter.discoverFields(
                        new AdapterContext("org-a", "user-a"), source("cloudcc"), "Account");

        assertThat(objects)
                .extracting(OntologyDataSourceAdapter.PhysicalObject::key)
                .containsExactly("Account", "DeliveryTask__c");
        assertThat(objects.get(0).metadataJson()).contains("\"prefix\":\"001\"");
        assertThat(fields)
                .extracting(OntologyDataSourceAdapter.PhysicalField::key)
                .containsExactly("id", "name");
        assertThat(fields.get(1).dataType()).isEqualTo("text");
        verify(cloudcc).getStandardObjects("org-a", "user-a");
        verify(cloudcc).getCustomObjects("org-a", "user-a");
        verify(cloudcc).getObjectFields("org-a", "user-a", "001");
    }

    @Test
    void reportsMalformedMetadataInsteadOfGuessingFields() {
        when(cloudcc.getStandardObjects("org-a", "user-a"))
                .thenReturn("✅ 标准对象共 1 个\n无法识别的对象行");
        when(cloudcc.getCustomObjects("org-a", "user-a"))
                .thenReturn("✅ 自定义对象列表为空");

        assertThatThrownBy(() -> adapter.discoverObjects(
                new AdapterContext("org-a", "user-a"), source("cloudcc")))
                .hasMessageContaining("CONNECTOR_DISCOVERY_FORMAT_UNRECOGNIZED");
    }

    @Test
    void compilesWhitelistedFiltersAndUsesCurrentUserPageQuery() {
        when(cloudcc.pageQueryRecords(
                "org-a",
                "user-a",
                "Account",
                "name,status",
                "status = 'IN_PROGRESS'",
                1,
                50)).thenReturn(new CloudccOpenApiService.PageRecords(
                        List.of(Map.of("name", "示例客户", "status", "IN_PROGRESS")),
                        1,
                        1,
                        1));

        PhysicalResult result = adapter.executeRead(
                new AdapterContext("org-a", "user-a"),
                source("cloudcc"),
                new PhysicalQuery(
                        "Account",
                        List.of("name", "status"),
                        List.of(new PhysicalFilter(
                                "status", OntologyDocument.Operator.EQ, "IN_PROGRESS")),
                        List.of(),
                        50));

        assertThat(result.rows()).containsExactly(
                Map.of("name", "示例客户", "status", "IN_PROGRESS"));
        assertThat(result.totalCount()).isEqualTo(1);
        verify(cloudcc).pageQueryRecords(
                "org-a", "user-a", "Account", "name,status",
                "status = 'IN_PROGRESS'", 1, 50);
    }

    @Test
    void escapesFilterLiteralsButNeverAcceptsAnUnmappedIdentifier() {
        when(cloudcc.pageQueryRecords(
                "org-a", "user-a", "Account", "name",
                "name = 'a'' OR 1=1 --'", 1, 20))
                .thenReturn(new CloudccOpenApiService.PageRecords(List.of(), 1, 0, 0));

        adapter.executeRead(
                new AdapterContext("org-a", "user-a"),
                source("cloudcc"),
                new PhysicalQuery(
                        "Account",
                        List.of("name"),
                        List.of(new PhysicalFilter(
                                "name", OntologyDocument.Operator.EQ, "a' OR 1=1 --")),
                        List.of(),
                        20));

        verify(cloudcc).pageQueryRecords(
                "org-a", "user-a", "Account", "name",
                "name = 'a'' OR 1=1 --'", 1, 20);

        assertThatThrownBy(() -> adapter.executeRead(
                new AdapterContext("org-a", "user-a"),
                source("cloudcc"),
                new PhysicalQuery(
                        "Account",
                        List.of("name) OR 1=1"),
                        List.of(),
                        List.of(),
                        20)))
                .hasMessageContaining("PHYSICAL_FIELD_NOT_ALLOWED");

        assertThatThrownBy(() -> adapter.executeRead(
                new AdapterContext("org-a", "user-a"),
                source("cloudcc"),
                new PhysicalQuery(
                        "Account",
                        List.of("name"),
                        List.of(new PhysicalFilter(
                                "name", OntologyDocument.Operator.EQ, "line-1\nline-2")),
                        List.of(),
                        20)))
                .hasMessage("QUERY_FILTER_VALUE_NOT_ALLOWED");
    }

    @Test
    void rejectsOversizedQueriesBeforeCallingTheConnector() {
        assertThatThrownBy(() -> adapter.executeRead(
                new AdapterContext("org-a", "user-a"),
                source("cloudcc"),
                new PhysicalQuery(
                        "Account", List.of("name"), List.of(), List.of(), 201)))
                .hasMessageContaining("QUERY_BUDGET_EXCEEDED");

        verifyNoInteractions(cloudcc);
    }

    @Test
    void rejectsOrderingInsteadOfLocallySortingATruncatedConnectorPage() {
        assertThatThrownBy(() -> adapter.executeRead(
                new AdapterContext("org-a", "user-a"),
                source("cloudcc"),
                new PhysicalQuery(
                        "Account",
                        List.of("name"),
                        List.of(),
                        List.of(new PhysicalOrder("name", Direction.DESC)),
                        50)))
                .hasMessage("QUERY_ORDER_NOT_SUPPORTED");

        verifyNoInteractions(cloudcc);
    }

    private DataSourceConfig source(String adapterKey) {
        return new DataSourceConfig(
                7L,
                41L,
                "business-source",
                "业务数据",
                OntologyDocument.SourceType.CONNECTOR,
                adapterKey,
                "{\"adapterKey\":\"" + adapterKey + "\"}",
                null);
    }
}
