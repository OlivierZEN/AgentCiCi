package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codehouse.ciciassistant.ontology.domain.OntologyConceptRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyMappingRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.ontology.service.OntologyCatalogTransactionService.MappingKey;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Transactional
class OntologyDraftPublishIntegrationTest {

    @Autowired
    private OntologyTenantPersistence persistence;

    @Autowired
    private OntologyWorkspaceRepository workspaces;

    @Autowired
    private OntologyConceptRepository concepts;

    @Autowired
    private OntologyMappingRepository mappings;

    @Autowired
    private OntologyVersionRepository versions;

    @Autowired
    private OntologyDraftService drafts;

    @Autowired
    private OntologyPublishService publisher;

    @Autowired
    private OntologyCatalogService catalog;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void replacesDraftChildrenWhilePreservingImmutablePublishedVersions() {
        TenantContext.setOrgId("org-task-213-service");
        TenantContext.setUserId("human-a");
        OntologyWorkspaceEntity workspace = persistence.saveForCurrentOrg(
                new OntologyWorkspaceEntity(
                        "org-task-213-service",
                        "project-delivery",
                        "初始名称",
                        "初始描述",
                        "human-a"));

        OntologyWorkspaceEntity savedDraft = drafts.saveDraft(
                "org-task-213-service",
                "human-a",
                workspace.getId(),
                0L,
                OntologyCompilerServiceTest.projectDeliveryDocument());

        assertThat(savedDraft.getDraftRevision()).isEqualTo(1L);
        assertThat(concepts.findByWorkspaceIdAndOrgIdOrderByIdAsc(
                workspace.getId(), "org-task-213-service")).hasSize(2);
        assertThat(mappings.findByWorkspaceIdAndOrgIdOrderByIdAsc(
                workspace.getId(), "org-task-213-service")).hasSize(6);

        long publishRevision = discoverAndValidateMappings(
                "org-task-213-service", "human-a", workspace.getId(), savedDraft);

        OntologyVersionEntity published = publisher.publish(
                "org-task-213-service",
                "human-a",
                workspace.getId(),
                publishRevision);

        assertThat(published.getVersionNo()).isEqualTo(1);
        assertThat(published.getSourceDraftRevision()).isEqualTo(publishRevision);
        assertThat(published.getContentHash()).hasSize(64);
        assertThat(published.getGraphqlSdl()).contains("type Project", "type Query");
        assertThat(versions.findByWorkspaceIdAndOrgIdOrderByVersionNoDesc(
                workspace.getId(), "org-task-213-service"))
                .extracting(OntologyVersionEntity::getVersionNo)
                .containsExactly(1);
        assertThat(workspaces.findByIdAndOrgId(
                workspace.getId(), "org-task-213-service"))
                .get()
                .satisfies(stored -> {
                    assertThat(stored.getStatus()).isEqualTo("PUBLISHED");
                    assertThat(stored.getPublishedVersion()).isEqualTo(1);
                });

        String firstSnapshot = published.getSnapshotJson();
        String firstHash = published.getContentHash();
        OntologyWorkspaceEntity revisedDraft = drafts.saveDraft(
                "org-task-213-service",
                "human-b",
                workspace.getId(),
                publishRevision,
                OntologyCompilerServiceTest.projectDeliveryDocument());
        assertThat(revisedDraft.getDraftRevision()).isEqualTo(publishRevision + 1);
        assertThat(revisedDraft.getStatus()).isEqualTo("DRAFT");
        assertThat(versions.findByWorkspaceIdAndOrgIdOrderByVersionNoDesc(
                workspace.getId(), "org-task-213-service"))
                .extracting(OntologyVersionEntity::getVersionNo)
                .containsExactly(1);

        TenantContext.setUserId("human-b");
        OntologyVersionEntity secondVersion = publisher.publish(
                "org-task-213-service",
                "human-b",
                workspace.getId(),
                publishRevision + 1);

        assertThat(secondVersion.getVersionNo()).isEqualTo(2);
        assertThat(secondVersion.getContentHash()).isNotEqualTo(firstHash);
        assertThat(versions.findByWorkspaceIdAndOrgIdOrderByVersionNoDesc(
                workspace.getId(), "org-task-213-service"))
                .extracting(OntologyVersionEntity::getVersionNo)
                .containsExactly(2, 1);
        assertThat(versions.findByWorkspaceIdAndOrgIdAndVersionNo(
                workspace.getId(), "org-task-213-service", 1))
                .get()
                .satisfies(firstVersion -> {
                    assertThat(firstVersion.getSnapshotJson()).isEqualTo(firstSnapshot);
                    assertThat(firstVersion.getContentHash()).isEqualTo(firstHash);
                });
    }

    @Test
    void roundTripsRelationEndpointAndJoinMappingChangesAtomically() {
        String orgId = "org-relation-roundtrip-" + UUID.randomUUID();
        TenantContext.setOrgId(orgId);
        TenantContext.setUserId("human-a");
        OntologyWorkspaceEntity workspace = persistence.saveForCurrentOrg(
                new OntologyWorkspaceEntity(
                        orgId, "project-delivery", "项目交付", "原子关系变更", "human-a"));
        OntologyWorkspaceEntity initial = drafts.saveDraft(
                orgId,
                "human-a",
                workspace.getId(),
                0L,
                OntologyCompilerServiceTest.projectDeliveryDocument());
        OntologyDocument loaded = drafts.loadDraft(orgId, workspace.getId(), initial);
        var originalMapping = mappings.findByWorkspaceIdAndOrgIdOrderByIdAsc(
                        workspace.getId(), orgId).stream()
                .filter(mapping -> "RELATION".equals(mapping.getTargetType()))
                .filter(mapping -> "contains-task".equals(mapping.getTargetKey()))
                .findFirst()
                .orElseThrow();

        List<OntologyDocument.Relation> reversedRelations = loaded.relations().stream()
                .map(relation -> "contains-task".equals(relation.key())
                        ? new OntologyDocument.Relation(
                                relation.key(), relation.name(), relation.description(),
                                "task", "project", OntologyDocument.Cardinality.MANY_TO_ONE,
                                relation.reverseLabel(), relation.forwardLabel(),
                                relation.queryable(), relation.enabled())
                        : relation)
                .toList();
        List<OntologyDocument.Mapping> changedMappings = loaded.mappings().stream()
                .map(mapping -> "RELATION".equals(mapping.targetType())
                                && "contains-task".equals(mapping.targetKey())
                        ? new OntologyDocument.Mapping(
                                mapping.targetType(), mapping.targetKey(), mapping.dataSourceId(),
                                "tasks", "project_id", "id", mapping.transform(),
                                mapping.confidence(), mapping.source(), mapping.validationStatus())
                        : mapping)
                .toList();
        OntologyDocument changed = new OntologyDocument(
                loaded.key(), loaded.name(), loaded.description(), loaded.concepts(),
                reversedRelations, loaded.metrics(), loaded.actions(),
                loaded.dataSources(), changedMappings);

        OntologyWorkspaceEntity revised = drafts.saveDraft(
                orgId, "human-a", workspace.getId(), 1L, changed);
        OntologyDocument roundTrip = drafts.loadDraft(orgId, workspace.getId(), revised);

        assertThat(revised.getDraftRevision()).isEqualTo(2L);
        assertThat(roundTrip.relations()).filteredOn(
                        relation -> "contains-task".equals(relation.key()))
                .singleElement()
                .satisfies(relation -> {
                    assertThat(relation.sourceConceptKey()).isEqualTo("task");
                    assertThat(relation.targetConceptKey()).isEqualTo("project");
                });
        assertThat(roundTrip.mappings())
                .filteredOn(mapping -> "RELATION".equals(mapping.targetType())
                        && "contains-task".equals(mapping.targetKey()))
                .singleElement()
                .satisfies(mapping -> {
                    assertThat(mapping.physicalObjectKey()).isEqualTo("tasks");
                    assertThat(mapping.physicalFieldKey()).isEqualTo("project_id");
                    assertThat(mapping.relationTargetFieldKey()).isEqualTo("id");
                    assertThat(mapping.validationStatus()).isEqualTo("PENDING");
                });
        var storedMapping = mappings.findByWorkspaceIdAndOrgIdOrderByIdAsc(
                        workspace.getId(), orgId).stream()
                .filter(mapping -> "RELATION".equals(mapping.getTargetType()))
                .filter(mapping -> "contains-task".equals(mapping.getTargetKey()))
                .findFirst()
                .orElseThrow();
        assertThat(storedMapping.getId()).isEqualTo(originalMapping.getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void serializesConcurrentRevisionChecksSoOnlyOneDraftSaveWins() throws Exception {
        String orgId = "org-concurrent-" + UUID.randomUUID();
        TenantContext.setOrgId(orgId);
        TenantContext.setUserId("human-a");
        Long workspaceId = new TransactionTemplate(transactionManager).execute(status ->
                persistence.saveForCurrentOrg(new OntologyWorkspaceEntity(
                        orgId, "project-delivery", "交付", "并发修订", "human-a")).getId());
        TenantContext.clear();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<String> first = concurrentSave(
                    executor, ready, start, orgId, workspaceId, "human-a");
            CompletableFuture<String> second = concurrentSave(
                    executor, ready, start, orgId, workspaceId, "human-b");
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("SAVED", "CONFLICT");
        } finally {
            executor.shutdownNow();
        }
    }

    private CompletableFuture<String> concurrentSave(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            String orgId,
            Long workspaceId,
            String userId) {
        return CompletableFuture.supplyAsync(() -> {
            TenantContext.setOrgId(orgId);
            TenantContext.setUserId(userId);
            ready.countDown();
            try {
                start.await();
                drafts.saveDraft(
                        orgId,
                        userId,
                        workspaceId,
                        0L,
                        OntologyCompilerServiceTest.projectDeliveryDocument());
                return "SAVED";
            } catch (com.codehouse.ciciassistant.common.error.ConflictException exception) {
                return "CONFLICT";
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } finally {
                TenantContext.clear();
            }
        }, executor);
    }

    private long discoverAndValidateMappings(
            String orgId,
            String userId,
            Long workspaceId,
            OntologyWorkspaceEntity workspace) {
        Long sourceId = drafts.loadDraft(orgId, workspaceId, workspace)
                .dataSources().getFirst().id();
        long revision = catalog.discoverObjects(
                orgId, userId, workspaceId, sourceId, workspace.getDraftRevision()).revision();
        revision = catalog.discoverFields(
                orgId, userId, workspaceId, sourceId, "projects", revision).revision();
        revision = catalog.discoverFields(
                orgId, userId, workspaceId, sourceId, "tasks", revision).revision();
        for (var mapping : mappings.findByWorkspaceIdAndOrgIdOrderByIdAsc(workspaceId, orgId)) {
            revision = catalog.validateMapping(
                    orgId,
                    userId,
                    workspaceId,
                    revision,
                    new MappingKey(
                            mapping.getTargetType(),
                            mapping.getTargetKey(),
                            mapping.getDataSourceId()))
                    .revision();
        }
        return revision;
    }
}
