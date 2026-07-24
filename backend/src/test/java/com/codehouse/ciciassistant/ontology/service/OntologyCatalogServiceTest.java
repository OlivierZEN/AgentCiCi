package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.AdapterContext;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.DataSourceConfig;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.MappingValidation;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalField;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalObject;
import com.codehouse.ciciassistant.common.error.DataSourceUnavailableException;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.MappingCommit;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.MappingKey;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.MappingPreparation;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.SourcePreparation;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class OntologyCatalogServiceTest {

    private final OntologyCatalogTransactionService transactions =
            mock(OntologyCatalogTransactionService.class);
    private final OntologyDataSourceAdapter adapter = mock(OntologyDataSourceAdapter.class);
    private OntologyCatalogService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId("org-a");
        TenantContext.setUserId("user-a");
        service = new OntologyCatalogService(
                transactions, List.of(adapter), new ObjectMapper());
        when(adapter.supports(any(DataSourceConfig.class))).thenReturn(true);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void invokesExternalObjectDiscoveryBeforeShortCommit() {
        SourcePreparation prepared = prepared(null, null);
        List<PhysicalObject> discovered = List.of(
                new PhysicalObject("projects", "项目", "INLINE", "{}"));
        when(transactions.prepareSource("org-a", 41L, 7L, 3L, null))
                .thenReturn(prepared);
        when(adapter.discoverObjects(any(), any())).thenReturn(discovered);
        when(transactions.commitObjects(prepared, "user-a", discovered)).thenReturn(4L);

        OntologyCatalogService.CatalogMutation<PhysicalObject> result = service.discoverObjects(
                "org-a", "user-a", 41L, 7L, 3L);

        assertThat(result.revision()).isEqualTo(4L);
        assertThat(result.items()).extracting(PhysicalObject::key).containsExactly("projects");
        InOrder order = inOrder(adapter, transactions);
        order.verify(adapter).discoverObjects(new AdapterContext("org-a", "user-a"), prepared.source());
        order.verify(transactions).commitObjects(prepared, "user-a", discovered);
    }

    @Test
    void invokesExternalFieldDiscoveryBeforeShortCommit() {
        SourcePreparation prepared = prepared("projects", 81L);
        List<PhysicalField> discovered = List.of(
                new PhysicalField("projects", "name", "项目名称", "text", false, false, "{}"));
        when(transactions.prepareSource("org-a", 41L, 7L, 3L, "projects"))
                .thenReturn(prepared);
        when(adapter.discoverFields(any(), any(), any())).thenReturn(discovered);
        when(transactions.commitFields(prepared, "user-a", discovered)).thenReturn(4L);

        OntologyCatalogService.CatalogMutation<PhysicalField> result = service.discoverFields(
                "org-a", "user-a", 41L, 7L, "projects", 3L);

        assertThat(result.revision()).isEqualTo(4L);
        assertThat(result.items()).extracting(PhysicalField::key).containsExactly("name");
        InOrder order = inOrder(adapter, transactions);
        order.verify(adapter).discoverFields(
                new AdapterContext("org-a", "user-a"), prepared.source(), "projects");
        order.verify(transactions).commitFields(prepared, "user-a", discovered);
    }

    @Test
    void validatesByStableIdentityAndCommitsOnlyAfterAdapterReturns() {
        MappingKey key = new MappingKey("property", "task.status", 7L);
        OntologyDocument.Mapping mapping = new OntologyDocument.Mapping(
                "PROPERTY", "task.status", 7L, "tasks", "status", null,
                "DIRECT", 1, "MANUAL", "PENDING");
        MappingPreparation prepared = new MappingPreparation(
                "org-a", 41L, 3L, key, source(), mapping);
        MappingValidation adapterResult = MappingValidation.success();
        MappingCommit committed = new MappingCommit(adapterResult, 4L);
        when(transactions.prepareMapping("org-a", 41L, 3L, key)).thenReturn(prepared);
        when(adapter.validateMapping(any(), any(), any())).thenReturn(adapterResult);
        when(transactions.commitMappingValidation(prepared, "user-a", adapterResult))
                .thenReturn(committed);

        MappingCommit result = service.validateMapping(
                "org-a", "user-a", 41L, 3L, key);

        assertThat(result).isEqualTo(committed);
        InOrder order = inOrder(adapter, transactions);
        order.verify(adapter).validateMapping(
                new AdapterContext("org-a", "user-a"), source(), mapping);
        order.verify(transactions).commitMappingValidation(prepared, "user-a", adapterResult);
    }

    @Test
    void preservesCatalogWhenExternalDiscoveryFails() {
        SourcePreparation prepared = prepared(null, null);
        when(transactions.prepareSource("org-a", 41L, 7L, 3L, null))
                .thenReturn(prepared);
        when(adapter.discoverObjects(any(), any()))
                .thenThrow(new IllegalStateException("CONNECTOR_DISCOVERY_FAILED"));

        assertThatThrownBy(() -> service.discoverObjects(
                "org-a", "user-a", 41L, 7L, 3L))
                .hasMessage("DATA_SOURCE_UNAVAILABLE");

        verify(transactions, never()).commitObjects(any(), any(), any());
    }

    @Test
    void rejectsAggregateCatalogResponsesAboveFourMiBBeforeAnyCommit() {
        AggregateGuardObjectMapper aggregateGuard = new AggregateGuardObjectMapper();
        service = new OntologyCatalogService(
                transactions, List.of(adapter), aggregateGuard);
        SourcePreparation objectPreparation = prepared(null, null);
        SourcePreparation fieldPreparation = prepared("projects", 81L);
        when(transactions.prepareSource("org-a", 41L, 7L, 3L, null))
                .thenReturn(objectPreparation);
        when(transactions.prepareSource("org-a", 41L, 7L, 3L, "projects"))
                .thenReturn(fieldPreparation);
        String boundedItemMetadata = "x".repeat(65_000);
        List<PhysicalObject> objects = IntStream.range(0, 100)
                .mapToObj(index -> new PhysicalObject(
                        "object-" + index, "对象" + index, "CONNECTOR", boundedItemMetadata))
                .toList();
        List<PhysicalField> fields = IntStream.range(0, 100)
                .mapToObj(index -> new PhysicalField(
                        "projects", "field-" + index, "字段" + index,
                        "TEXT", true, false, boundedItemMetadata))
                .toList();
        when(adapter.discoverObjects(any(), any())).thenReturn(objects);
        when(adapter.discoverFields(any(), any(), any())).thenReturn(fields);

        assertThatThrownBy(() -> service.discoverObjects(
                "org-a", "user-a", 41L, 7L, 3L))
                .isInstanceOf(DataSourceUnavailableException.class)
                .hasMessage("DATA_SOURCE_UNAVAILABLE");
        assertThat(aggregateGuard.itemWrites()).isPositive().isLessThan(objects.size());
        aggregateGuard.resetItemWrites();
        assertThatThrownBy(() -> service.discoverFields(
                "org-a", "user-a", 41L, 7L, "projects", 3L))
                .isInstanceOf(DataSourceUnavailableException.class)
                .hasMessage("DATA_SOURCE_UNAVAILABLE");
        assertThat(aggregateGuard.itemWrites()).isPositive().isLessThan(fields.size());

        verify(transactions, never()).commitObjects(any(), any(), any());
        verify(transactions, never()).commitFields(any(), any(), any());
    }

    @Test
    void acceptsSingleObjectAndFieldMetadataAtExactlySixtyFourKiB() {
        SourcePreparation objectPreparation = prepared(null, null);
        SourcePreparation fieldPreparation = prepared("projects", 81L);
        String exactMetadata = "x".repeat(64 * 1024);
        List<PhysicalObject> objects = List.of(new PhysicalObject(
                "projects", "项目", "CONNECTOR", exactMetadata));
        List<PhysicalField> fields = List.of(new PhysicalField(
                "projects", "name", "名称", "TEXT", true, false, exactMetadata));
        when(transactions.prepareSource("org-a", 41L, 7L, 3L, null))
                .thenReturn(objectPreparation);
        when(transactions.prepareSource("org-a", 41L, 7L, 3L, "projects"))
                .thenReturn(fieldPreparation);
        when(adapter.discoverObjects(any(), any())).thenReturn(objects);
        when(adapter.discoverFields(any(), any(), any())).thenReturn(fields);
        when(transactions.commitObjects(objectPreparation, "user-a", objects)).thenReturn(4L);
        when(transactions.commitFields(fieldPreparation, "user-a", fields)).thenReturn(4L);

        assertThat(service.discoverObjects(
                "org-a", "user-a", 41L, 7L, 3L).revision()).isEqualTo(4L);
        assertThat(service.discoverFields(
                "org-a", "user-a", 41L, 7L, "projects", 3L).revision()).isEqualTo(4L);

        verify(transactions).commitObjects(objectPreparation, "user-a", objects);
        verify(transactions).commitFields(fieldPreparation, "user-a", fields);
    }

    @Test
    void rejectsSingleObjectAndFieldMetadataAboveSixtyFourKiBBeforeAnyCommit() {
        SourcePreparation objectPreparation = prepared(null, null);
        SourcePreparation fieldPreparation = prepared("projects", 81L);
        String oversizedMetadata = "x".repeat(64 * 1024 + 1);
        when(transactions.prepareSource("org-a", 41L, 7L, 3L, null))
                .thenReturn(objectPreparation);
        when(transactions.prepareSource("org-a", 41L, 7L, 3L, "projects"))
                .thenReturn(fieldPreparation);
        when(adapter.discoverObjects(any(), any())).thenReturn(List.of(new PhysicalObject(
                "projects", "项目", "CONNECTOR", oversizedMetadata)));
        when(adapter.discoverFields(any(), any(), any())).thenReturn(List.of(new PhysicalField(
                "projects", "name", "名称", "TEXT", true, false, oversizedMetadata)));

        assertThatThrownBy(() -> service.discoverObjects(
                "org-a", "user-a", 41L, 7L, 3L))
                .isInstanceOf(DataSourceUnavailableException.class)
                .hasMessage("DATA_SOURCE_UNAVAILABLE");
        assertThatThrownBy(() -> service.discoverFields(
                "org-a", "user-a", 41L, 7L, "projects", 3L))
                .isInstanceOf(DataSourceUnavailableException.class)
                .hasMessage("DATA_SOURCE_UNAVAILABLE");

        verify(transactions, never()).commitObjects(any(), any(), any());
        verify(transactions, never()).commitFields(any(), any(), any());
    }

    @Test
    void failsClosedWithoutPartialCommitWhenOneBatchValidationThrowsOrLeaksDetails() {
        MappingKey firstKey = new MappingKey("PROPERTY", "task.status", 7L);
        MappingKey secondKey = new MappingKey("PROPERTY", "project.name", 7L);
        OntologyDocument.Mapping firstMapping = new OntologyDocument.Mapping(
                "PROPERTY", "task.status", 7L, "tasks", "status", null,
                "DIRECT", 1, "MANUAL", "PENDING");
        OntologyDocument.Mapping secondMapping = new OntologyDocument.Mapping(
                "PROPERTY", "project.name", 7L, "projects", "name", null,
                "DIRECT", 1, "MANUAL", "PENDING");
        MappingPreparation firstPrepared = new MappingPreparation(
                "org-a", 41L, 3L, firstKey, source(), firstMapping);
        MappingPreparation secondPrepared = new MappingPreparation(
                "org-a", 41L, 3L, secondKey, source(), secondMapping);
        when(transactions.prepareMapping("org-a", 41L, 3L, firstKey))
                .thenReturn(firstPrepared);
        when(transactions.prepareMapping("org-a", 41L, 3L, secondKey))
                .thenReturn(secondPrepared);
        when(adapter.validateMapping(any(), any(), any()))
                .thenReturn(MappingValidation.success())
                .thenThrow(new IllegalStateException("secret-token=should-not-leak"));

        assertThatThrownBy(() -> service.validateMappings(
                "org-a", "user-a", 41L, 3L, List.of(firstKey, secondKey)))
                .hasMessage("DATA_SOURCE_UNAVAILABLE");

        verify(transactions, never()).commitMappingValidations(any(), any(), any());
    }

    @Test
    void rejectsImpersonatedContextBeforePreparingDatabaseWork() {
        assertThatThrownBy(() -> service.discoverObjects(
                "org-a", "user-b", 41L, 7L, 3L))
                .hasMessage("ONTOLOGY_CATALOG_CONTEXT_MISMATCH");

        verifyNoInteractions(transactions);
    }

    private SourcePreparation prepared(String objectKey, Long objectId) {
        return new SourcePreparation(
                "org-a", 41L, 3L, source(), objectKey, objectId);
    }

    private DataSourceConfig source() {
        return new DataSourceConfig(
                7L,
                41L,
                "business-source",
                "业务数据",
                OntologyDocument.SourceType.CONNECTOR,
                "example",
                "{\"adapterKey\":\"example\"}",
                null);
    }

    private static final class AggregateGuardObjectMapper extends ObjectMapper {

        private int itemWrites;

        @Override
        public byte[] writeValueAsBytes(Object value) throws JsonProcessingException {
            if (value instanceof List<?>) {
                throw new AssertionError("whole catalog arrays must not be materialized");
            }
            return super.writeValueAsBytes(value);
        }

        @Override
        public void writeValue(JsonGenerator generator, Object value) throws IOException {
            itemWrites++;
            super.writeValue(generator, value);
        }

        int itemWrites() {
            return itemWrites;
        }

        void resetItemWrites() {
            itemWrites = 0;
        }
    }
}
