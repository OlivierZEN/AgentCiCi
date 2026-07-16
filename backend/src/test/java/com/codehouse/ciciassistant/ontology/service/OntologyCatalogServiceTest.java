package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.AdapterContext;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.DataSourceConfig;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.MappingValidation;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalField;
import com.codehouse.ciciassistant.ontology.adapter.OntologyDataSourceAdapter.PhysicalObject;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class OntologyCatalogServiceTest {

    private final OntologyDataSourceRepository dataSources = mock(OntologyDataSourceRepository.class);
    private final OntologyPhysicalObjectRepository objects = mock(OntologyPhysicalObjectRepository.class);
    private final OntologyPhysicalFieldRepository fields = mock(OntologyPhysicalFieldRepository.class);
    private final OntologyMappingRepository mappings = mock(OntologyMappingRepository.class);
    private final OntologyTenantPersistence persistence = mock(OntologyTenantPersistence.class);
    private final OntologyDataSourceAdapter adapter = mock(OntologyDataSourceAdapter.class);
    private OntologyCatalogService service;

    @BeforeEach
    void setUp() {
        TenantContext.setOrgId("org-a");
        TenantContext.setUserId("user-a");
        service = new OntologyCatalogService(
                dataSources,
                objects,
                fields,
                mappings,
                persistence,
                List.of(adapter),
                new ObjectMapper());
        when(adapter.supports(any(DataSourceConfig.class))).thenReturn(true);
        when(persistence.saveForCurrentOrg(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void refreshesDiscoveredObjectsAndDeletesOnlyStaleScopedRows() {
        OntologyDataSourceEntity source = source();
        OntologyPhysicalObjectEntity current = object(81L, "Account", "旧名称");
        OntologyPhysicalObjectEntity stale = object(82L, "Removed__c", "待清理");
        when(dataSources.findByIdAndWorkspaceIdAndOrgId(7L, 41L, "org-a"))
                .thenReturn(Optional.of(source));
        when(objects.findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                7L, 41L, "org-a"))
                .thenReturn(List.of(current, stale));
        when(adapter.discoverObjects(any(), any())).thenReturn(List.of(
                new PhysicalObject("Account", "客户", "STANDARD", "{\"prefix\":\"001\"}"),
                new PhysicalObject("Delivery__c", "交付", "CUSTOM", "{\"prefix\":\"a10\"}")));

        List<PhysicalObject> result = service.discoverObjects(
                "org-a", "user-a", 41L, 7L);

        assertThat(result).extracting(PhysicalObject::key)
                .containsExactly("Account", "Delivery__c");
        assertThat(current.getName()).isEqualTo("客户");
        assertThat(current.getMetadataJson()).contains("001");
        verify(objects).deleteByIdAndWorkspaceIdAndOrgId(82L, 41L, "org-a");
        ArgumentCaptor<OntologyPhysicalObjectEntity> saved =
                ArgumentCaptor.forClass(OntologyPhysicalObjectEntity.class);
        verify(persistence, org.mockito.Mockito.times(2)).saveForCurrentOrg(saved.capture());
        assertThat(saved.getAllValues()).extracting(OntologyPhysicalObjectEntity::getObjectKey)
                .containsExactly("Account", "Delivery__c");
        verify(adapter).discoverObjects(
                new AdapterContext("org-a", "user-a"),
                new DataSourceConfig(
                        7L, 41L, "business-source", "业务数据",
                        com.codehouse.ciciassistant.ontology.model.OntologyDocument.SourceType.CONNECTOR,
                        "example", "{\"adapterKey\":\"example\"}", null));
    }

    @Test
    void refreshesFieldsForTheSelectedPersistedObject() {
        OntologyDataSourceEntity source = source();
        OntologyPhysicalObjectEntity object = object(81L, "Account", "客户");
        OntologyPhysicalFieldEntity current = field(91L, "name", "旧客户名称");
        OntologyPhysicalFieldEntity stale = field(92L, "removed", "待清理");
        when(dataSources.findByIdAndWorkspaceIdAndOrgId(7L, 41L, "org-a"))
                .thenReturn(Optional.of(source));
        when(objects.findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                7L, 41L, "org-a"))
                .thenReturn(List.of(object));
        when(fields.findByPhysicalObjectIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
                81L, 41L, "org-a"))
                .thenReturn(List.of(current, stale));
        when(adapter.discoverFields(any(), any(), org.mockito.ArgumentMatchers.eq("Account")))
                .thenReturn(List.of(
                        new PhysicalField("Account", "name", "客户名称", "text", false, false, "{}"),
                        new PhysicalField("Account", "status", "状态", "select", true, false, "{}")));

        List<PhysicalField> result = service.discoverFields(
                "org-a", "user-a", 41L, 7L, "Account");

        assertThat(result).extracting(PhysicalField::key)
                .containsExactly("name", "status");
        assertThat(current.getName()).isEqualTo("客户名称");
        assertThat(current.isNullable()).isFalse();
        verify(fields).deleteByIdAndWorkspaceIdAndOrgId(92L, 41L, "org-a");
        ArgumentCaptor<OntologyPhysicalFieldEntity> saved =
                ArgumentCaptor.forClass(OntologyPhysicalFieldEntity.class);
        verify(persistence, org.mockito.Mockito.times(2)).saveForCurrentOrg(saved.capture());
        assertThat(saved.getAllValues()).extracting(OntologyPhysicalFieldEntity::getFieldKey)
                .containsExactly("name", "status");
    }

    @Test
    void persistsAdapterMappingValidationThroughTenantPersistence() {
        OntologyDataSourceEntity source = source();
        OntologyMappingEntity mapping = mapping();
        when(mappings.findByIdAndWorkspaceIdAndOrgId(101L, 41L, "org-a"))
                .thenReturn(Optional.of(mapping));
        when(dataSources.findByIdAndWorkspaceIdAndOrgId(7L, 41L, "org-a"))
                .thenReturn(Optional.of(source));
        when(adapter.validateMapping(any(), any(), any()))
                .thenReturn(MappingValidation.invalid(
                        "PHYSICAL_FIELD_NOT_FOUND", "Mapped field was not discovered"));

        MappingValidation result = service.validateMapping(
                "org-a", "user-a", 41L, 101L);

        assertThat(result.valid()).isFalse();
        assertThat(mapping.getValidationStatus()).isEqualTo("INVALID");
        assertThat(mapping.getLastValidatedAt()).isNotNull();
        verify(persistence).saveForCurrentOrg(mapping);
    }

    @Test
    void rejectsImpersonatedCatalogContextBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.discoverObjects(
                "org-a", "user-b", 41L, 7L))
                .hasMessageContaining("ONTOLOGY_CATALOG_CONTEXT_MISMATCH");

        verifyNoInteractions(dataSources, objects, fields, mappings);
    }

    @Test
    void preservesTheLastSuccessfulDirectoryWhenDiscoveryFails() {
        when(dataSources.findByIdAndWorkspaceIdAndOrgId(7L, 41L, "org-a"))
                .thenReturn(Optional.of(source()));
        when(adapter.discoverObjects(any(), any()))
                .thenThrow(new IllegalStateException("CONNECTOR_DISCOVERY_FAILED"));

        assertThatThrownBy(() -> service.discoverObjects(
                "org-a", "user-a", 41L, 7L))
                .hasMessage("CONNECTOR_DISCOVERY_FAILED");

        verifyNoInteractions(objects, fields, mappings);
        verify(persistence, org.mockito.Mockito.never()).saveForCurrentOrg(any());
    }

    private OntologyDataSourceEntity source() {
        OntologyDataSourceEntity source = new OntologyDataSourceEntity(
                "org-a",
                41L,
                "business-source",
                "业务数据",
                "CONNECTOR",
                "{\"adapterKey\":\"example\"}",
                null,
                "creator");
        ReflectionTestUtils.setField(source, "id", 7L);
        return source;
    }

    private OntologyPhysicalObjectEntity object(Long id, String key, String name) {
        OntologyPhysicalObjectEntity object = new OntologyPhysicalObjectEntity(
                "org-a", 41L, 7L, key, name, "OLD", "{}");
        ReflectionTestUtils.setField(object, "id", id);
        return object;
    }

    private OntologyPhysicalFieldEntity field(Long id, String key, String name) {
        OntologyPhysicalFieldEntity field = new OntologyPhysicalFieldEntity(
                "org-a", 41L, 81L, key, name, "old", true, false, "{}");
        ReflectionTestUtils.setField(field, "id", id);
        return field;
    }

    private OntologyMappingEntity mapping() {
        OntologyMappingEntity mapping = new OntologyMappingEntity(
                "org-a",
                41L,
                "PROPERTY",
                "task.status",
                7L,
                "Task__c",
                "status__c",
                null,
                "DIRECT",
                BigDecimal.ONE,
                "MANUAL",
                "PENDING",
                "creator");
        ReflectionTestUtils.setField(mapping, "id", 101L);
        return mapping;
    }
}
