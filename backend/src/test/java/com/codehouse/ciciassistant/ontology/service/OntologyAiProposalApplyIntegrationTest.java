package com.codehouse.ciciassistant.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ai.service.AliyunBailianClient;
import com.codehouse.ciciassistant.ai.service.AliyunBailianClient.ChatCompletionResult;
import com.codehouse.ciciassistant.ai.service.ModelRouterService;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.codehouse.ciciassistant.ontology.domain.OntologyAiProposalEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyAiProposalRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyVersionRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class OntologyAiProposalApplyIntegrationTest {

    @Autowired
    private OntologyTenantPersistence persistence;

    @Autowired
    private OntologyWorkspaceRepository workspaces;

    @Autowired
    private OntologyAiProposalRepository proposals;

    @Autowired
    private OntologyVersionRepository versions;

    @Autowired
    private OntologyDraftService drafts;

    @Autowired
    private OntologyPublishService publisher;

    @Autowired
    private OntologyAiProposalService proposalService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ModelRouterService modelRouter;

    @MockBean
    private ModelProviderService modelProviders;

    @MockBean
    private AliyunBailianClient modelClient;

    @AfterEach
    void clearTenantAndFailureTrigger() {
        TenantContext.clear();
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS task213_fail_proposal_apply ON ontology_ai_proposal");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS task213_reject_proposal_apply()");
    }

    @Test
    void concurrentApplyWithLargePrivateConfigSucceedsOnceAndLeavesVersionImmutable() throws Exception {
        Fixture fixture = createReadyPublishedFixture();
        TenantContext.clear();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<String> first = concurrentApply(
                    executor, ready, start, fixture, "user-a");
            CompletableFuture<String> second = concurrentApply(
                    executor, ready, start, fixture, "user-a");
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("APPLIED", "REJECTED");
        } finally {
            executor.shutdownNow();
        }

        TenantContext.setOrgId(fixture.orgId());
        TenantContext.setUserId("user-a");
        OntologyWorkspaceEntity workspace = workspaces
                .findByIdAndOrgId(fixture.workspaceId(), fixture.orgId()).orElseThrow();
        OntologyAiProposalEntity proposal = proposals
                .findByIdAndWorkspaceIdAndOrgId(
                        fixture.proposalId(), fixture.workspaceId(), fixture.orgId())
                .orElseThrow();
        List<OntologyVersionEntity> storedVersions = versions
                .findByWorkspaceIdAndOrgIdOrderByVersionNoDesc(
                        fixture.workspaceId(), fixture.orgId());

        assertThat(workspace.getDraftRevision()).isEqualTo(2L);
        assertThat(workspace.getStatus()).isEqualTo("DRAFT");
        assertThat(proposal.getStatus()).isEqualTo("APPLIED");
        assertThat(storedVersions).hasSize(1);
        assertThat(storedVersions.getFirst().getVersionNo()).isEqualTo(1);
        assertThat(storedVersions.getFirst().getContentHash()).isEqualTo(fixture.versionHash());
        assertThat(storedVersions.getFirst().getSnapshotJson()).isEqualTo(fixture.versionSnapshot());
        OntologyDocument appliedDraft = drafts.loadDraft(
                fixture.orgId(), fixture.workspaceId(), workspace);
        assertThat(appliedDraft.dataSources())
                .containsExactlyInAnyOrderElementsOf(fixture.original().dataSources());
        assertThat(appliedDraft.mappings())
                .containsExactlyInAnyOrderElementsOf(fixture.original().mappings());
    }

    @Test
    void databaseFailureAfterDraftSaveRollsBackDraftProposalAndVersions() throws Exception {
        Fixture fixture = createReadyPublishedFixture();
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION task213_reject_proposal_apply()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    IF NEW.id = %d AND NEW.status = 'APPLIED' THEN
                        RAISE EXCEPTION 'task213 forced proposal apply failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """.formatted(fixture.proposalId()));
        jdbcTemplate.execute("""
                CREATE TRIGGER task213_fail_proposal_apply
                BEFORE UPDATE ON ontology_ai_proposal
                FOR EACH ROW
                EXECUTE FUNCTION task213_reject_proposal_apply()
                """);

        assertThatThrownBy(() -> proposalService.apply(
                fixture.orgId(), "user-a", fixture.proposalId(), 1L))
                .isInstanceOf(DataAccessException.class);

        OntologyWorkspaceEntity workspace = workspaces
                .findByIdAndOrgId(fixture.workspaceId(), fixture.orgId()).orElseThrow();
        OntologyAiProposalEntity proposal = proposals
                .findByIdAndWorkspaceIdAndOrgId(
                        fixture.proposalId(), fixture.workspaceId(), fixture.orgId())
                .orElseThrow();
        List<OntologyVersionEntity> storedVersions = versions
                .findByWorkspaceIdAndOrgIdOrderByVersionNoDesc(
                        fixture.workspaceId(), fixture.orgId());

        assertThat(workspace.getDraftRevision()).isEqualTo(1L);
        assertThat(workspace.getStatus()).isEqualTo("PUBLISHED");
        assertThat(proposal.getStatus()).isEqualTo("READY");
        assertThat(storedVersions).hasSize(1);
        assertThat(storedVersions.getFirst().getContentHash()).isEqualTo(fixture.versionHash());
        OntologyDocument unchangedDraft = drafts.loadDraft(
                fixture.orgId(), fixture.workspaceId(), workspace);
        assertThat(unchangedDraft).isEqualTo(fixture.original());
    }

    @Test
    void archivedWorkspaceRejectsApplyWithoutChangingDraftProposalOrVersion() throws Exception {
        Fixture fixture = createReadyPublishedFixture();
        jdbcTemplate.update(
                "UPDATE ontology_workspace SET status = 'ARCHIVED' WHERE id = ? AND org_id = ?",
                fixture.workspaceId(), fixture.orgId());

        assertThatThrownBy(() -> proposalService.apply(
                fixture.orgId(), "user-a", fixture.proposalId(), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI_PROPOSAL_INVALID");

        OntologyWorkspaceEntity workspace = workspaces
                .findByIdAndOrgId(fixture.workspaceId(), fixture.orgId()).orElseThrow();
        OntologyAiProposalEntity proposal = proposals
                .findByIdAndWorkspaceIdAndOrgId(
                        fixture.proposalId(), fixture.workspaceId(), fixture.orgId())
                .orElseThrow();
        List<OntologyVersionEntity> storedVersions = versions
                .findByWorkspaceIdAndOrgIdOrderByVersionNoDesc(
                        fixture.workspaceId(), fixture.orgId());

        assertThat(workspace.getStatus()).isEqualTo("ARCHIVED");
        assertThat(workspace.getDraftRevision()).isEqualTo(1L);
        assertThat(proposal.getStatus()).isEqualTo("READY");
        assertThat(storedVersions).hasSize(1);
        assertThat(storedVersions.getFirst().getContentHash()).isEqualTo(fixture.versionHash());
        assertThat(storedVersions.getFirst().getSnapshotJson()).isEqualTo(fixture.versionSnapshot());
        assertThat(drafts.loadDraft(fixture.orgId(), fixture.workspaceId(), workspace))
                .isEqualTo(fixture.original());
    }

    private Fixture createReadyPublishedFixture() throws Exception {
        String orgId = "org-ai-proposal-" + UUID.randomUUID();
        TenantContext.setOrgId(orgId);
        TenantContext.setUserId("user-a");
        OntologyDocument input = withLargePrivateConfig(
                OntologyCompilerServiceTest.projectDeliveryDocument());
        OntologyWorkspaceEntity workspace = persistence.saveForCurrentOrg(
                new OntologyWorkspaceEntity(
                        orgId, "initial", "初始领域", "AI 提案原子测试", "user-a"));
        OntologyWorkspaceEntity savedWorkspace = drafts.saveDraft(
                orgId, "user-a", workspace.getId(), 0L, input);
        OntologyDocument original = drafts.loadDraft(
                orgId, workspace.getId(), savedWorkspace);
        OntologyVersionEntity version = publisher.publish(
                orgId, "user-a", workspace.getId(), 1L);

        when(modelRouter.route(orgId, "ontology-modeling")).thenReturn(Map.of(
                "provider", "provider-a",
                "modelName", "model-a"));
        when(modelProviders.credentialsForProvider(orgId, "provider-a")).thenReturn(Map.of(
                "enabled", "true",
                "apiBaseUrl", "https://models.invalid/v1",
                "apiKey", "test-key"));
        when(modelClient.chatCompletionWithCredentials(
                eq("model-a"), anyList(), isNull(), eq(true),
                eq("https://models.invalid/v1"), eq("test-key"),
                eq(OntologyAiProposalService.MAX_OUTPUT_TOKENS),
                eq(OntologyAiProposalService.MAX_RESPONSE_BYTES)))
                .thenReturn(new ChatCompletionResult(
                        "assistant",
                        objectMapper.writeValueAsString(withoutAssets(original)),
                        List.of(),
                        "stop",
                        10,
                        20));

        OntologyAiProposalService.ProposalView proposal = proposalService.propose(
                orgId,
                "user-a",
                workspace.getId(),
                new OntologyAiProposalService.ProposalCommand(
                        "项目交付领域，包含项目、任务和负责人",
                        List.of(),
                        "DOMAIN_FIRST"));
        assertThat(proposal.status()).isEqualTo("READY");
        assertThat(proposal.candidate().dataSources())
                .extracting(OntologyDocument.DataSource::configJson)
                .containsOnlyNulls();
        assertThat(objectMapper.writeValueAsString(proposal))
                .doesNotContain("server-secret", "private-row");
        Map<String, Object> storedProposal = jdbcTemplate.queryForMap(
                "SELECT payload_json, diff_json, validation_json, instruction "
                        + "FROM ontology_ai_proposal WHERE id = ?",
                proposal.id());
        assertThat(objectMapper.writeValueAsString(storedProposal))
                .doesNotContain("server-secret", "private-row");
        assertThat(String.valueOf(storedProposal.get("payload_json"))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(OntologyAiProposalService.MAX_RESPONSE_BYTES);
        assertThat(workspaces.findByIdAndOrgId(workspace.getId(), orgId).orElseThrow()
                .getDraftRevision()).isEqualTo(1L);
        assertThat(versions.findByWorkspaceIdAndOrgIdOrderByVersionNoDesc(
                workspace.getId(), orgId)).hasSize(1);
        return new Fixture(
                orgId,
                workspace.getId(),
                proposal.id(),
                original,
                version.getContentHash(),
                version.getSnapshotJson());
    }

    private CompletableFuture<String> concurrentApply(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            Fixture fixture,
            String userId) {
        return CompletableFuture.supplyAsync(() -> {
            TenantContext.setOrgId(fixture.orgId());
            TenantContext.setUserId(userId);
            ready.countDown();
            try {
                start.await();
                proposalService.apply(
                        fixture.orgId(), userId, fixture.proposalId(), 1L);
                return "APPLIED";
            } catch (IllegalArgumentException exception) {
                assertThat(exception).hasMessage("AI_PROPOSAL_INVALID");
                return "REJECTED";
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } finally {
                TenantContext.clear();
            }
        }, executor);
    }

    private OntologyDocument withoutAssets(OntologyDocument document) {
        return new OntologyDocument(
                document.key(),
                document.name(),
                document.description(),
                document.concepts(),
                document.relations(),
                document.metrics(),
                document.actions(),
                List.of(),
                List.of());
    }

    private OntologyDocument withLargePrivateConfig(OntologyDocument document) {
        OntologyDocument.DataSource source = document.dataSources().getFirst();
        String config = "{\"apiKey\":\"server-secret\",\"records\":[{\"name\":\"private-row\","
                + "\"padding\":\""
                + "x".repeat(OntologyAiProposalService.MAX_RESPONSE_BYTES + 1_024)
                + "\"}]}";
        return new OntologyDocument(
                document.key(),
                document.name(),
                document.description(),
                document.concepts(),
                document.relations(),
                document.metrics(),
                document.actions(),
                List.of(new OntologyDocument.DataSource(
                        source.id(), source.key(), source.name(), source.type(), config)),
                document.mappings());
    }

    private record Fixture(
            String orgId,
            Long workspaceId,
            Long proposalId,
            OntologyDocument original,
            String versionHash,
            String versionSnapshot) {
    }
}
