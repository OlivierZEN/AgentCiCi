package com.codehouse.ciciassistant.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codehouse.ciciassistant.agent.domain.AgentKnowledgeBindingEntity;
import com.codehouse.ciciassistant.agent.domain.AgentKnowledgeBindingRepository;
import com.codehouse.ciciassistant.ai.service.RagService;
import com.codehouse.ciciassistant.billing.domain.BillingCreditLedgerRepository;
import com.codehouse.ciciassistant.billing.domain.UsageMeterEventRepository;
import com.codehouse.ciciassistant.kb.domain.KbChunkRepository;
import com.codehouse.ciciassistant.kb.domain.KbDocumentRepository;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseRepository;
import com.codehouse.ciciassistant.kb.service.KbAccessControlService;
import com.codehouse.ciciassistant.kb.service.KbDataQualityService;
import com.codehouse.ciciassistant.kb.service.KnowledgeBaseService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.profiles.active=default",
        "app.kb.indexing.mode=local",
        "app.kb.vector-store=memory",
        "app.kb.storage-dir=target/kb-test-files"
})
class KnowledgeBaseLifecycleIntegrationTest {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private RagService ragService;

    @Autowired
    private KbChunkRepository chunkRepository;

    @Autowired
    private KbDataQualityService kbDataQualityService;

    @Autowired
    private KbDocumentRepository documentRepository;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private AgentKnowledgeBindingRepository agentKnowledgeBindingRepository;

    @Autowired
    private UsageMeterEventRepository usageMeterEventRepository;

    @Autowired
    private BillingCreditLedgerRepository creditLedgerRepository;

    @Test
    void shouldDeleteDocumentChunksAndVectorsAndStopRag() {
        Fixture fixture = createPublishedDocument("obsolete policy clause alpha");

        assertThat(ragService.retrieveContext(fixture.companyId(), List.of(fixture.kbIdText()), "alpha clause"))
                .anyMatch(item -> item.contains("obsolete policy"));
        assertThat(chunkRepository.countByCompanyIdAndDocumentIdAndStatusAndEnabledTrue(
                fixture.companyId(), fixture.documentId(), "ACTIVE")).isEqualTo(1);

        Map<String, Object> deleted = knowledgeBaseService.deleteDocument(fixture.companyId(), fixture.documentId());

        assertThat(deleted.get("status")).isEqualTo("DELETED");
        assertThat(deleted.get("cleanupStatus")).isEqualTo("COMPLETED");
        assertThat(chunkRepository.countByCompanyIdAndDocumentIdAndStatusAndEnabledTrue(
                fixture.companyId(), fixture.documentId(), "ACTIVE")).isZero();
        assertThat(documentRepository.findByIdAndCompanyId(fixture.documentId(), fixture.companyId()))
                .get()
                .extracting("status")
                .isEqualTo("DELETED");
        assertThat(ragService.retrieveContext(fixture.companyId(), List.of(fixture.kbIdText()), "alpha clause"))
                .isEmpty();
    }

    @Test
    void shouldUnpublishDocumentFromRetrieval() {
        Fixture fixture = createPublishedDocument("temporary launch playbook beta");

        Map<String, Object> unpublished = knowledgeBaseService.unpublishDocument(fixture.companyId(), fixture.documentId());

        assertThat(unpublished.get("status")).isEqualTo("UNPUBLISHED");
        assertThat(unpublished.get("cleanupStatus")).isEqualTo("COMPLETED");
        assertThat(chunkRepository.countByCompanyIdAndDocumentIdAndStatusAndEnabledTrue(
                fixture.companyId(), fixture.documentId(), "ACTIVE")).isZero();
        assertThat(ragService.retrieveContext(fixture.companyId(), List.of(fixture.kbIdText()), "launch beta"))
                .isEmpty();
    }

    @Test
    void shouldReindexDocumentIdempotently() {
        Fixture fixture = createPublishedDocument("stable support policy gamma");

        knowledgeBaseService.reindexDocument(fixture.companyId(), fixture.documentId());

        assertThat(chunkRepository.countByCompanyIdAndDocumentIdAndStatusAndEnabledTrue(
                fixture.companyId(), fixture.documentId(), "ACTIVE")).isEqualTo(1);
        assertThat(ragService.retrieveContext(fixture.companyId(), List.of(fixture.kbIdText()), "support gamma"))
                .hasSize(1)
                .first()
                .asString()
                .contains("stable support policy");
        RagService.RetrievalResult detailed = ragService.retrieveDetailed(
                fixture.companyId(),
                List.of(fixture.kbIdText()),
                "support gamma");
        assertThat(detailed.context()).isNotEmpty();
        assertThat(detailed.knowledgeBases())
                .extracting(RagService.RetrievedKnowledgeBase::name)
                .contains("Lifecycle KB");
        assertThat(detailed.sources())
                .extracting(RagService.RetrievedSource::documentName)
                .contains("policy.txt");
        assertThat(detailed.sources().get(0).toPayload())
                .containsKeys("confidence", "trustLevel", "freshnessStatus", "documentIndexVersion", "chunkContentHash");
        assertThat(detailed.timingsMs()).containsKey("total");
    }

    @Test
    void shouldFilterRetrievalByDocumentAccessGrant() {
        Fixture fixture = createPublishedDocument("restricted payroll policy theta");
        knowledgeBaseService.replaceDocumentAccessGrants(
                fixture.companyId(),
                fixture.documentId(),
                "admin-user",
                List.of(new KbAccessControlService.GrantInput("USER", "allowed-user", null)));

        RagService.RetrievalResult allowed = ragService.retrieveDetailed(
                fixture.companyId(),
                List.of(fixture.kbIdText()),
                "payroll theta",
                Map.of(),
                KbAccessControlService.AccessPrincipal.user("allowed-user", List.of("ORG_USER")));
        RagService.RetrievalResult denied = ragService.retrieveDetailed(
                fixture.companyId(),
                List.of(fixture.kbIdText()),
                "payroll theta",
                Map.of(),
                KbAccessControlService.AccessPrincipal.user("blocked-user", List.of("ORG_USER")));

        assertThat(allowed.context()).anyMatch(item -> item.contains("restricted payroll"));
        assertThat(allowed.permissionFilteredCount()).isZero();
        assertThat(denied.context()).isEmpty();
        assertThat(denied.permissionFilteredCount()).isGreaterThan(0);
    }

    @Test
    void shouldAuditIndexDriftForHealthyKnowledgeBase() {
        Fixture fixture = createPublishedDocument("healthy drift baseline iota");

        Map<String, Object> audit = knowledgeBaseService.auditIndexDrift(fixture.companyId(), false);

        assertThat(audit).containsEntry("status", "OK");
        assertThat(audit).containsEntry("repairRequested", false);
        assertThat(audit).containsEntry("missingVectorChunkCount", 0);
        assertThat(audit).containsEntry("embeddingMismatchChunkCount", 0);
        assertThat(audit).containsEntry("publishedDocumentWithoutChunkCount", 0);
        assertThat(audit).containsEntry("staleSyncDocumentCount", 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> embeddingDriftCheck = (Map<String, Object>) audit.get("embeddingDriftCheck");
        assertThat(embeddingDriftCheck).containsEntry("status", "AVAILABLE");
        assertThat(embeddingDriftCheck).containsEntry("mismatchCount", 0);
    }

    @Test
    void shouldRecordCreditsForDocumentAndManualChunkIndexing() {
        String companyId = "kb-billing-" + UUID.randomUUID();
        Map<String, Object> kb = knowledgeBaseService.createKnowledgeBase(companyId, "Billing KB", "test");
        Long kbId = ((Number) kb.get("id")).longValue();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "billing.txt",
                "text/plain",
                "credits billing policy document".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> document = knowledgeBaseService.uploadDocument(companyId, kbId, file);
        Long documentId = ((Number) document.get("id")).longValue();
        knowledgeBaseService.publishDocument(companyId, documentId);
        knowledgeBaseService.reindexDocument(companyId, documentId);
        knowledgeBaseService.addChunk(companyId, String.valueOf(kbId), "manual billing chunk", "");

        var indexingEvents = usageMeterEventRepository.findTop100ByCompanyIdOrderByOccurredAtDesc(companyId).stream()
                .filter(item -> "kb_indexing".equals(item.getBillableDomain()))
                .toList();
        assertThat(indexingEvents).hasSize(3);
        assertThat(indexingEvents)
                .extracting(item -> item.getBillableItemCode())
                .containsOnly("kb_indexing_credit");
        assertThat(indexingEvents)
                .allSatisfy(event -> {
                    assertThat(event.getWorkCreditQuantity()).isEqualByComparingTo("0.20");
                    assertThat(event.getBillingType()).isEqualTo("platform_paid");
                    assertThat(event.getMetadataJson()).contains("\"officialPricingItem\":\"Credits 包\"");
                });
        assertThat(creditLedgerRepository.findByCompanyIdOrderByIdAsc(companyId).stream()
                .filter(entry -> "usage_debit".equals(entry.getEntryType()))
                .toList()).hasSize(3);
    }

    @Test
    void shouldExposeUploadPolicyAndIndexTextPdf() throws Exception {
        String companyId = "kb-policy-" + UUID.randomUUID();
        Map<String, Object> policy = knowledgeBaseService.uploadPolicy(companyId);
        @SuppressWarnings("unchecked")
        List<String> allowedExtensions = (List<String>) policy.get("allowedExtensions");
        @SuppressWarnings("unchecked")
        List<String> unsupportedParserLabels = (List<String>) policy.get("unsupportedParserLabels");
        @SuppressWarnings("unchecked")
        Map<String, Object> serviceApi = (Map<String, Object>) policy.get("serviceApi");

        assertThat(allowedExtensions)
                .contains("txt", "md", "csv", "json", "docx", "pdf");
        assertThat(unsupportedParserLabels).isEmpty();
        assertThat((String) policy.get("pdfPolicy")).contains("支持文本型 PDF");
        assertThat(serviceApi).containsEntry("apiAccessEnabled", false);

        Map<String, Object> kb = knowledgeBaseService.createKnowledgeBase(companyId, "Policy KB", "test");
        Long kbId = ((Number) kb.get("id")).longValue();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "brief.pdf",
                "application/pdf",
                textPdf("pdf parser readiness omega"));

        Map<String, Object> document = knowledgeBaseService.uploadDocument(companyId, kbId, file);
        Long documentId = ((Number) document.get("id")).longValue();
        knowledgeBaseService.publishDocument(companyId, documentId);

        assertThat(ragService.retrieveContext(companyId, List.of(String.valueOf(kbId)), "omega"))
                .anyMatch(item -> item.contains("pdf parser readiness"));
    }

    @Test
    void shouldAuditRegisteredVectorsWithoutOrphans() {
        Fixture fixture = createPublishedDocument("vector audit readiness epsilon");

        Map<String, Object> audit = knowledgeBaseService.auditVectorStore(fixture.companyId());

        assertThat(audit).containsEntry("success", true);
        assertThat(audit).containsEntry("status", "OK");
        assertThat((Integer) audit.get("registeredCount")).isEqualTo(1);
        assertThat((Integer) audit.get("scannedCount")).isEqualTo(1);
        assertThat((Integer) audit.get("orphanCount")).isZero();
    }

    @Test
    void shouldDeleteKnowledgeBaseCascadeDataAndRuntimeBindings() {
        Fixture fixture = createPublishedDocument("retired operating guide delta");
        agentKnowledgeBindingRepository.save(new AgentKnowledgeBindingEntity(
                fixture.companyId(),
                "agent-" + UUID.randomUUID(),
                fixture.kbId(),
                1,
                true));

        Map<String, Object> deleted = knowledgeBaseService.deleteKnowledgeBase(fixture.companyId(), fixture.kbId());

        assertThat(deleted.get("status")).isEqualTo("DELETED");
        assertThat(deleted.get("cleanupStatus")).isEqualTo("COMPLETED");
        assertThat(agentKnowledgeBindingRepository.countByCompanyIdAndKnowledgeBaseId(fixture.companyId(), fixture.kbId()))
                .isZero();
        assertThat(chunkRepository.countByCompanyIdAndKnowledgeBaseIdAndStatusAndEnabledTrue(
                fixture.companyId(), fixture.kbIdText(), "ACTIVE")).isZero();
        assertThat(knowledgeBaseRepository.findByIdAndCompanyId(fixture.kbId(), fixture.companyId()))
                .get()
                .extracting("status")
                .isEqualTo("DELETED");
        assertThat(knowledgeBaseService.listKnowledgeBases(fixture.companyId()))
                .extracting(item -> item.get("id"))
                .doesNotContain(fixture.kbId());
        assertThat(ragService.retrieveContext(fixture.companyId(), List.of(fixture.kbIdText()), "operating delta"))
                .isEmpty();
    }

    @Test
    void shouldSupportChunkPreviewAndRetrievalTestWithKbSettings() {
        Fixture fixture = createPublishedDocument("customer onboarding handbook version alpha beta gamma");

        Map<String, Object> settings = knowledgeBaseService.updateKnowledgeBaseSettings(
                fixture.companyId(),
                fixture.kbId(),
                new KnowledgeBaseService.KbSettingsCommand(120, 20, "\\n", "VECTOR", 3, 0.0, "local", "local-hash", 1024));
        assertThat(settings.get("chunkSize")).isEqualTo(120);
        assertThat(settings.get("topK")).isEqualTo(3);

        Map<String, Object> preview = knowledgeBaseService.previewChunking(
                fixture.companyId(),
                fixture.kbId(),
                new KnowledgeBaseService.ChunkPreviewCommand(
                        "line1\nline2\nline3\nline4",
                        null,
                        null,
                        null,
                        3));
        assertThat(preview.get("totalChunks")).isNotNull();
        assertThat((List<?>) preview.get("previewChunks")).isNotEmpty();

        Map<String, Object> retrieval = knowledgeBaseService.testRetrieval(
                fixture.companyId(),
                fixture.kbId(),
                new KnowledgeBaseService.RetrievalTestCommand("onboarding alpha", null, null, null, null));
        assertThat(retrieval.get("topK")).isEqualTo(3);
        assertThat((Integer) retrieval.get("hitCount")).isGreaterThanOrEqualTo(1);

        List<Map<String, Object>> logs = knowledgeBaseService.listRetrievalLogs(fixture.companyId(), fixture.kbId(), 5);
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0)).containsEntry("query", "onboarding alpha");
    }

    @Test
    void shouldRunRetrievalEvaluationSuite() {
        Fixture fixture = createPublishedDocument("evaluation recall handbook omega source");

        Map<String, Object> suite = knowledgeBaseService.createEvalSuite(
                fixture.companyId(),
                fixture.kbId(),
                new KnowledgeBaseService.EvalSuiteCommand("Recall Smoke", "expected source recall"));
        Long suiteId = ((Number) suite.get("id")).longValue();

        Map<String, Object> evalCase = knowledgeBaseService.addEvalCase(
                fixture.companyId(),
                suiteId,
                new KnowledgeBaseService.EvalCaseCommand(
                        "recall omega",
                        fixture.documentId(),
                        "policy.txt",
                        "recall handbook",
                        0.0,
                        fixture.documentId() + 1000,
                        Map.of()));

        Map<String, Object> run = knowledgeBaseService.runEvalSuite(fixture.companyId(), suiteId);
        List<Map<String, Object>> runs = knowledgeBaseService.listEvalRuns(fixture.companyId(), suiteId);
        List<Map<String, Object>> results = knowledgeBaseService.listEvalRunResults(
                fixture.companyId(),
                ((Number) run.get("id")).longValue());

        assertThat(evalCase).containsEntry("query", "recall omega");
        assertThat(run).containsEntry("status", "PASSED");
        assertThat(run).containsEntry("caseCount", 1);
        assertThat(run).containsEntry("passedCount", 1);
        assertThat(run).containsEntry("forbiddenSourceViolations", 0);
        assertThat((Double) run.get("expectedSourceRecall")).isEqualTo(1.0);
        assertThat(runs).isNotEmpty();
        assertThat(results).hasSize(1);
        assertThat(results.get(0)).containsEntry("expectedHit", true);
    }

    @Test
    void shouldSyncExternalApiDataSourceIntoPublishedDocument() {
        String companyId = "kb-sync-" + UUID.randomUUID();
        Map<String, Object> kb = knowledgeBaseService.createKnowledgeBase(companyId, "Sync KB", "test");
        Long kbId = ((Number) kb.get("id")).longValue();

        Map<String, Object> source = knowledgeBaseService.createDataSource(
                companyId,
                kbId,
                new KnowledgeBaseService.DataSourceCommand(
                        "EXTERNAL_API",
                        "Policy Feed",
                        Map.of(
                                "externalId", "policy-feed-1",
                                "title", "policy-feed",
                                "content", "connector sync lambda policy")));
        Long sourceId = ((Number) source.get("id")).longValue();

        Map<String, Object> job = knowledgeBaseService.syncDataSource(companyId, sourceId, "MANUAL");
        List<Map<String, Object>> jobs = knowledgeBaseService.listSyncJobs(companyId, sourceId);

        assertThat(job).containsEntry("status", "SUCCEEDED");
        assertThat(job).containsEntry("documentCount", 1);
        assertThat(jobs).isNotEmpty();
        assertThat(ragService.retrieveContext(companyId, List.of(String.valueOf(kbId)), "lambda policy"))
                .anyMatch(item -> item.contains("connector sync"));
    }

    @Test
    void shouldSupportChunkToggleAndMetadataFilteringInRetrievalTest() {
        Fixture fixture = createPublishedDocument("sales policy for east region only");

        knowledgeBaseService.createMetadataField(
                fixture.companyId(),
                fixture.kbId(),
                new KnowledgeBaseService.MetadataFieldCommand("region", "区域", "string"));
        knowledgeBaseService.updateDocumentMetadata(
                fixture.companyId(),
                fixture.documentId(),
                Map.of("region", "east"));

        List<Map<String, Object>> chunks = knowledgeBaseService.listDocumentChunks(fixture.companyId(), fixture.documentId());
        assertThat(chunks).isNotEmpty();
        Long chunkId = ((Number) chunks.get(0).get("id")).longValue();

        Map<String, Object> filtered = knowledgeBaseService.testRetrieval(
                fixture.companyId(),
                fixture.kbId(),
                new KnowledgeBaseService.RetrievalTestCommand(
                        "sales policy",
                        5,
                        0.0,
                        "VECTOR",
                        Map.of("region", "east")));
        assertThat(filtered.get("hitCount")).isEqualTo(1);

        RagService.RetrievalResult runtimeFiltered = ragService.retrieveDetailed(
                fixture.companyId(),
                List.of(fixture.kbIdText()),
                "sales policy",
                Map.of("region", "east"));
        assertThat(runtimeFiltered.context()).hasSize(1);
        assertThat(runtimeFiltered.sources()).hasSize(1);
        assertThat(runtimeFiltered.metadataFilters()).containsEntry("region", "east");

        RagService.RetrievalResult runtimeMiss = ragService.retrieveDetailed(
                fixture.companyId(),
                List.of(fixture.kbIdText()),
                "sales policy",
                Map.of("region", "west"));
        assertThat(runtimeMiss.context()).isEmpty();
        assertThat(runtimeMiss.sources()).isEmpty();

        knowledgeBaseService.setChunkEnabled(fixture.companyId(), chunkId, false);
        Map<String, Object> afterDisable = knowledgeBaseService.testRetrieval(
                fixture.companyId(),
                fixture.kbId(),
                new KnowledgeBaseService.RetrievalTestCommand(
                        "sales policy",
                        5,
                        0.0,
                        "VECTOR",
                        Map.of("region", "east")));
        assertThat(afterDisable.get("hitCount")).isEqualTo(0);
    }

    @Test
    void shouldScanCleanAndAnnotateKnowledgeBaseData() {
        String companyId = "kb-quality-" + UUID.randomUUID();
        Map<String, Object> kb = knowledgeBaseService.createKnowledgeBase(companyId, "Quality KB", "test");
        Long kbId = ((Number) kb.get("id")).longValue();
        assertThat(kbDataQualityService.listSources(companyId))
                .extracting(item -> item.get("sourceKey"))
                .contains("kb:" + kbId);
        knowledgeBaseService.addChunk(companyId, String.valueOf(kbId), "duplicate deployment policy content", "");
        knowledgeBaseService.addChunk(companyId, String.valueOf(kbId), "duplicate deployment policy content", "");
        knowledgeBaseService.addChunk(companyId, String.valueOf(kbId), "tiny", "");
        Map<String, Object> dirtyChunk = knowledgeBaseService.addChunk(
                companyId,
                String.valueOf(kbId),
                "private cloud deployment guide\nDISCLAIMER: remove this footer",
                "");
        Long dirtyChunkId = ((Number) dirtyChunk.get("id")).longValue();

        Map<String, Object> rule = kbDataQualityService.createRule(
                companyId,
                kbId,
                "admin-user",
                new KbDataQualityService.QualityRuleCommand(
                        "Remove footer",
                        "REGEX_REMOVE",
                        "DISCLAIMER:.*",
                        "",
                        true));
        Long ruleId = ((Number) rule.get("id")).longValue();

        Map<String, Object> scan = kbDataQualityService.startScan(
                companyId,
                kbId,
                "admin-user",
                new KbDataQualityService.QualityScanCommand("MANUAL"));
        List<Map<String, Object>> issues = kbDataQualityService.listIssues(companyId, kbId, "OPEN");
        assertThat((Integer) scan.get("duplicateIssueCount")).isEqualTo(2);
        assertThat((Integer) scan.get("invalidIssueCount")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) scan.get("regexIssueCount")).isEqualTo(1);
        assertThat(issues).extracting(item -> item.get("issueType"))
                .contains("DUPLICATE", "TOO_SHORT", "REGEX_MATCH");

        Map<String, Object> preview = kbDataQualityService.previewRule(
                companyId,
                ruleId,
                new KbDataQualityService.QualityApplyCommand(List.of(dirtyChunkId), List.of(), Map.of(), 10));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> previewItems = (List<Map<String, Object>>) preview.get("items");
        assertThat(previewItems).hasSize(1);
        assertThat((String) previewItems.get(0).get("before")).contains("DISCLAIMER");
        assertThat((String) previewItems.get(0).get("after")).doesNotContain("DISCLAIMER");

        Map<String, Object> apply = kbDataQualityService.applyRule(
                companyId,
                ruleId,
                "admin-user",
                new KbDataQualityService.QualityApplyCommand(
                        List.of(dirtyChunkId),
                        List.of(),
                        Map.of(dirtyChunkId, (String) previewItems.get(0).get("contentHash")),
                        10));
        assertThat(apply).containsEntry("updatedCount", 1);
        assertThat(chunkRepository.findByIdAndCompanyId(dirtyChunkId, companyId)).get()
                .extracting("content")
                .asString()
                .doesNotContain("DISCLAIMER");

        Map<String, Object> suggestions = kbDataQualityService.suggestAnnotations(
                companyId,
                kbId,
                "admin-user",
                new KbDataQualityService.AnnotationSuggestCommand("CHUNK", "topic", 10));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestionItems = (List<Map<String, Object>>) suggestions.get("items");
        assertThat(suggestionItems).isNotEmpty();
        Long suggestionId = ((Number) suggestionItems.get(0).get("id")).longValue();
        Map<String, Object> accepted = kbDataQualityService.acceptSuggestion(
                companyId,
                suggestionId,
                "admin-user",
                new KbDataQualityService.AnnotationReviewCommand(null));

        assertThat(accepted).containsEntry("status", "ACCEPTED");
        assertThat(kbDataQualityService.listChunkAnnotations(companyId, kbId))
                .extracting(item -> item.get("fieldKey"))
                .contains("topic");
    }

    @Test
    void shouldSupportBatchDocumentOperations() {
        Fixture fixture = createPublishedDocument("batch operation handbook zeta");

        Map<String, Object> disableResult = knowledgeBaseService.batchSetDocumentEnabled(
                fixture.companyId(),
                List.of(fixture.documentId()),
                false);
        assertThat(disableResult.get("successCount")).isEqualTo(1);
        assertThat(ragService.retrieveContext(fixture.companyId(), List.of(fixture.kbIdText()), "handbook zeta"))
                .isEmpty();

        Map<String, Object> enableResult = knowledgeBaseService.batchSetDocumentEnabled(
                fixture.companyId(),
                List.of(fixture.documentId()),
                true);
        assertThat(enableResult.get("successCount")).isEqualTo(1);
        assertThat(ragService.retrieveContext(fixture.companyId(), List.of(fixture.kbIdText()), "handbook zeta"))
                .isNotEmpty();
    }

    @Test
    void shouldRejectUnknownMetadataFilterField() {
        Fixture fixture = createPublishedDocument("quality assurance playbook theta");
        assertThatThrownBy(() -> knowledgeBaseService.testRetrieval(
                fixture.companyId(),
                fixture.kbId(),
                new KnowledgeBaseService.RetrievalTestCommand(
                        "quality assurance",
                        5,
                        0.0,
                        "VECTOR",
                        Map.of("unknown_field", "x"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown metadata filter field");
    }

    @Test
    void shouldIndexDocxUpload() throws Exception {
        String companyId = "kb-docx-" + UUID.randomUUID();
        Map<String, Object> kb = knowledgeBaseService.createKnowledgeBase(companyId, "DOCX KB", "test");
        Long kbId = ((Number) kb.get("id")).longValue();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "CloudCC运维千问.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes("CloudCC 运维千问 包含巡检流程和告警处置。"));

        Map<String, Object> document = knowledgeBaseService.uploadDocument(companyId, kbId, file);
        Long documentId = ((Number) document.get("id")).longValue();
        Map<String, Object> published = knowledgeBaseService.publishDocument(companyId, documentId);

        assertThat(published.get("status")).isEqualTo("PUBLISHED");
        assertThat(chunkRepository.countByCompanyIdAndDocumentIdAndStatusAndEnabledTrue(
                companyId, documentId, "ACTIVE")).isEqualTo(1);
        assertThat(ragService.retrieveContext(companyId, List.of(String.valueOf(kbId)), "巡检流程"))
                .anyMatch(item -> item.contains("告警处置"));
    }

    private Fixture createPublishedDocument(String content) {
        String companyId = "kb-life-" + UUID.randomUUID();
        Map<String, Object> kb = knowledgeBaseService.createKnowledgeBase(companyId, "Lifecycle KB", "test");
        Long kbId = ((Number) kb.get("id")).longValue();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> document = knowledgeBaseService.uploadDocument(companyId, kbId, file);
        Long documentId = ((Number) document.get("id")).longValue();
        Map<String, Object> published = knowledgeBaseService.publishDocument(companyId, documentId);
        assertThat(published.get("status")).isEqualTo("PUBLISHED");
        return new Fixture(companyId, kbId, documentId);
    }

    private byte[] docxBytes(String text) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body><w:p><w:r><w:t>%s</w:t></w:r></w:p></w:body>
                    </w:document>
                    """).formatted(text).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private byte[] textPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private record Fixture(String companyId, Long kbId, Long documentId) {

        String kbIdText() {
            return String.valueOf(kbId);
        }
    }
}
