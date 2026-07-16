package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.ontology.domain.AbstractOntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyActionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyActionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyConceptEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyConceptRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyDataSourceRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyMetricEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyMetricRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalFieldRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPhysicalObjectRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyPropertyEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyPropertyRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyRelationEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyRelationRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OntologyDraftServiceTest {

    @Mock
    private OntologyWorkspaceRepository workspaces;
    @Mock
    private OntologyConceptRepository concepts;
    @Mock
    private OntologyPropertyRepository properties;
    @Mock
    private OntologyRelationRepository relations;
    @Mock
    private OntologyMetricRepository metrics;
    @Mock
    private OntologyActionRepository actions;
    @Mock
    private OntologyDataSourceRepository dataSources;
    @Mock
    private OntologyPhysicalFieldRepository physicalFields;
    @Mock
    private OntologyPhysicalObjectRepository physicalObjects;
    @Mock
    private OntologyMappingRepository mappings;
    @Mock
    private OntologyTenantPersistence persistence;

    private OntologyDraftService service;

    @BeforeEach
    void setUp() {
        service = new OntologyDraftService(
                workspaces,
                concepts,
                properties,
                relations,
                metrics,
                actions,
                dataSources,
                mappings,
                persistence,
                new ObjectMapper());
        TenantContext.setOrgId("org-a");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void rejectsRevisionConflictBeforeDeletingOrWritingAnything() {
        OntologyWorkspaceEntity workspace = workspace(41L, 3L);
        when(workspaces.findForUpdateByIdAndOrgId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));

        assertThatThrownBy(() -> service.saveDraft(
                "org-a", "user-a", 41L, 2L,
                OntologyCompilerServiceTest.projectDeliveryDocument()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("ONTOLOGY_REVISION_CONFLICT");

        verifyNoInteractions(persistence);
        verify(mappings, never()).deleteByWorkspaceIdAndOrgId(any(), any());
        verify(concepts, never()).deleteByWorkspaceIdAndOrgId(any(), any());
    }

    @Test
    void replacesDraftChildrenAndRoutesEveryWriteThroughTenantPersistence() {
        OntologyWorkspaceEntity workspace = workspace(41L, 0L);
        when(workspaces.findForUpdateByIdAndOrgId(41L, "org-a"))
                .thenReturn(Optional.of(workspace));
        OntologyDataSourceEntity existingSource = new OntologyDataSourceEntity(
                "org-a",
                41L,
                "delivery-source",
                "旧交付数据",
                "INLINE_SAMPLE",
                "{\"old\":true}",
                "[{\"id\":1}]",
                "creator");
        ReflectionTestUtils.setField(existingSource, "id", 99L);
        when(dataSources.findByWorkspaceIdAndOrgIdAndKey(
                41L, "org-a", "delivery-source"))
                .thenReturn(Optional.of(existingSource));
        AtomicLong ids = new AtomicLong(100);
        when(persistence.saveForCurrentOrg(any())).thenAnswer(invocation -> {
            OntologyTenantEntity entity = invocation.getArgument(0);
            if (entity instanceof AbstractOntologyWorkspaceEntity child && child.getId() == null) {
                ReflectionTestUtils.setField(child, "id", ids.incrementAndGet());
            }
            return entity;
        });

        OntologyWorkspaceEntity saved = service.saveDraft(
                "org-a", "user-a", 41L, 0L,
                OntologyCompilerServiceTest.projectDeliveryDocument());

        assertThat(saved.getDraftRevision()).isEqualTo(1L);
        assertThat(saved.getKey()).isEqualTo("project-delivery");
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        verify(mappings).deleteByWorkspaceIdAndOrgId(41L, "org-a");
        verify(physicalFields, never()).deleteByWorkspaceIdAndOrgId(41L, "org-a");
        verify(physicalObjects, never()).deleteByWorkspaceIdAndOrgId(41L, "org-a");
        verify(properties).deleteByWorkspaceIdAndOrgId(41L, "org-a");
        verify(relations).deleteByWorkspaceIdAndOrgId(41L, "org-a");
        verify(metrics).deleteByWorkspaceIdAndOrgId(41L, "org-a");
        verify(actions).deleteByWorkspaceIdAndOrgId(41L, "org-a");
        verify(concepts).deleteByWorkspaceIdAndOrgId(41L, "org-a");
        verify(dataSources, never()).deleteByWorkspaceIdAndOrgId(41L, "org-a");
        verify(persistence).flushForCurrentOrg("org-a");

        verify(persistence, times(2)).saveForCurrentOrg(isA(OntologyConceptEntity.class));
        verify(persistence, times(3)).saveForCurrentOrg(isA(OntologyPropertyEntity.class));
        verify(persistence).saveForCurrentOrg(isA(OntologyRelationEntity.class));
        verify(persistence).saveForCurrentOrg(isA(OntologyMetricEntity.class));
        verify(persistence).saveForCurrentOrg(isA(OntologyActionEntity.class));
        verify(persistence).saveForCurrentOrg(isA(OntologyDataSourceEntity.class));
        verify(persistence, times(6)).saveForCurrentOrg(isA(OntologyMappingEntity.class));
        verify(persistence, atLeastOnce()).saveForCurrentOrg(workspace);

        ArgumentCaptor<OntologyDataSourceEntity> sourceCaptor =
                ArgumentCaptor.forClass(OntologyDataSourceEntity.class);
        verify(persistence).saveForCurrentOrg(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue()).isSameAs(existingSource);
        assertThat(sourceCaptor.getValue().getSampleDataJson()).isEqualTo("[{\"id\":1}]");
        assertThat(sourceCaptor.getValue().getName()).isEqualTo("交付数据");
        ArgumentCaptor<OntologyMappingEntity> mappingCaptor =
                ArgumentCaptor.forClass(OntologyMappingEntity.class);
        verify(persistence, times(6)).saveForCurrentOrg(mappingCaptor.capture());
        assertThat(mappingCaptor.getAllValues())
                .extracting(OntologyMappingEntity::getDataSourceId)
                .containsOnly(sourceCaptor.getValue().getId());
    }

    private OntologyWorkspaceEntity workspace(Long id, Long draftRevision) {
        OntologyWorkspaceEntity workspace = new OntologyWorkspaceEntity(
                "org-a", "old-key", "旧名称", "旧描述", "creator");
        ReflectionTestUtils.setField(workspace, "id", id);
        ReflectionTestUtils.setField(workspace, "draftRevision", draftRevision);
        return workspace;
    }
}
