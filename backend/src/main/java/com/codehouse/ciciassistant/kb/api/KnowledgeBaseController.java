package com.codehouse.ciciassistant.kb.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.kb.service.KbAccessControlService;
import com.codehouse.ciciassistant.kb.service.KbDataQualityService;
import com.codehouse.ciciassistant.kb.service.KnowledgeBaseService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KbDataQualityService kbDataQualityService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
                                   KbDataQualityService kbDataQualityService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.kbDataQualityService = kbDataQualityService;
    }

    @PostMapping
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> createKnowledgeBase(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.createKnowledgeBase(orgId, request.name(), request.description()));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listKnowledgeBases() {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listKnowledgeBases(orgId));
    }

    @PutMapping("/{id}")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> updateKnowledgeBase(@PathVariable Long id,
                                                                @Valid @RequestBody CreateKnowledgeBaseRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.updateKnowledgeBase(orgId, id, request.name(), request.description()));
    }

    @GetMapping("/{id}/settings")
    public ApiResponse<Map<String, Object>> getKnowledgeBaseSettings(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.getKnowledgeBaseSettings(orgId, id));
    }

    @PutMapping("/{id}/settings")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> updateKnowledgeBaseSettings(@PathVariable Long id,
                                                                         @Valid @RequestBody UpdateKnowledgeBaseSettingsRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.updateKnowledgeBaseSettings(orgId, id, new KnowledgeBaseService.KbSettingsCommand(
                request.chunkSize(),
                request.chunkOverlap(),
                request.chunkDelimiter(),
                request.retrievalStrategy(),
                request.topK(),
                request.scoreThreshold(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.embeddingDimension()
        )));
    }

    @GetMapping("/embedding-models")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listEmbeddingModelOptions() {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listEmbeddingModelOptions(orgId));
    }

    @GetMapping("/upload-policy")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> uploadPolicy() {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.uploadPolicy(orgId));
    }

    @GetMapping("/vector-store/audit")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> auditVectorStore() {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.auditVectorStore(orgId));
    }

    @PostMapping("/drift/audit")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> auditIndexDrift(@RequestBody(required = false) DriftAuditRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.auditIndexDrift(orgId, request != null && Boolean.TRUE.equals(request.repair())));
    }

    @DeleteMapping("/{id}")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> deleteKnowledgeBase(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.deleteKnowledgeBase(orgId, id));
    }

    @GetMapping("/{kbId}/documents")
    public ApiResponse<List<Map<String, Object>>> listDocuments(@PathVariable Long kbId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listDocuments(orgId, kbId));
    }

    @PutMapping("/documents/{id}/rename")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> renameDocument(@PathVariable Long id,
                                                            @Valid @RequestBody RenameDocumentRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.renameDocument(orgId, id, request.name()));
    }

    @PostMapping("/documents/{id}/enable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> enableDocument(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.setDocumentEnabled(orgId, id, true));
    }

    @PostMapping("/documents/{id}/disable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> disableDocument(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.setDocumentEnabled(orgId, id, false));
    }

    @PostMapping("/documents/{id}/archive")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> archiveDocument(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.setDocumentArchived(orgId, id, true));
    }

    @PostMapping("/documents/{id}/unarchive")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> unarchiveDocument(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.setDocumentArchived(orgId, id, false));
    }

    @PostMapping("/documents/batch/enable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchEnableDocuments(@Valid @RequestBody BatchIdsRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.batchSetDocumentEnabled(orgId, request.ids(), true));
    }

    @PostMapping("/documents/batch/disable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchDisableDocuments(@Valid @RequestBody BatchIdsRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.batchSetDocumentEnabled(orgId, request.ids(), false));
    }

    @PostMapping("/documents/batch/archive")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchArchiveDocuments(@Valid @RequestBody BatchIdsRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.batchSetDocumentArchived(orgId, request.ids(), true));
    }

    @PostMapping("/documents/batch/unarchive")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchUnarchiveDocuments(@Valid @RequestBody BatchIdsRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.batchSetDocumentArchived(orgId, request.ids(), false));
    }

    @PostMapping("/documents/batch/delete")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchDeleteDocuments(@Valid @RequestBody BatchIdsRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.batchDeleteDocuments(orgId, request.ids()));
    }

    @GetMapping("/documents/{id}/chunks")
    public ApiResponse<List<Map<String, Object>>> listDocumentChunks(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listDocumentChunks(orgId, id));
    }

    @PostMapping("/documents/upload")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> uploadDocument(@RequestParam("knowledgeBaseId") Long knowledgeBaseId,
                                                           @RequestParam("file") MultipartFile file) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.uploadDocument(orgId, knowledgeBaseId, file));
    }

    @GetMapping("/{kbId}/data-sources")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listDataSources(@PathVariable Long kbId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listDataSources(orgId, kbId));
    }

    @PostMapping("/{kbId}/data-sources")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> createDataSource(@PathVariable Long kbId,
                                                              @Valid @RequestBody DataSourceRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.createDataSource(
                orgId,
                kbId,
                new KnowledgeBaseService.DataSourceCommand(
                        request.sourceType(),
                        request.name(),
                        request.config())));
    }

    @PostMapping("/data-sources/{sourceId}/sync")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> syncDataSource(@PathVariable Long sourceId,
                                                            @RequestBody(required = false) SyncDataSourceRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.syncDataSource(
                orgId,
                sourceId,
                request == null ? "MANUAL" : request.triggerType()));
    }

    @GetMapping("/data-sources/{sourceId}/sync-jobs")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listSyncJobs(@PathVariable Long sourceId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listSyncJobs(orgId, sourceId));
    }

    @PostMapping("/{kbId}/chunking/preview")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> previewChunking(@PathVariable Long kbId,
                                                             @Valid @RequestBody ChunkPreviewRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.previewChunking(orgId, kbId, new KnowledgeBaseService.ChunkPreviewCommand(
                request.text(),
                request.chunkSize(),
                request.chunkOverlap(),
                request.chunkDelimiter(),
                request.maxChunks()
        )));
    }

    @PostMapping("/{kbId}/retrieval/test")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> testRetrieval(@PathVariable Long kbId,
                                                           @Valid @RequestBody RetrievalTestRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.testRetrieval(orgId, kbId, new KnowledgeBaseService.RetrievalTestCommand(
                request.query(),
                request.topK(),
                request.scoreThreshold(),
                request.retrievalStrategy(),
                request.metadataFilters()
        )));
    }

    @GetMapping("/{kbId}/retrieval/logs")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listRetrievalLogs(@PathVariable Long kbId,
                                                                     @RequestParam(name = "limit", required = false) Integer limit) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listRetrievalLogs(orgId, kbId, limit));
    }

    @GetMapping("/{kbId}/eval/suites")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listEvalSuites(@PathVariable Long kbId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listEvalSuites(orgId, kbId));
    }

    @PostMapping("/{kbId}/eval/suites")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> createEvalSuite(@PathVariable Long kbId,
                                                             @Valid @RequestBody EvalSuiteRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.createEvalSuite(
                orgId,
                kbId,
                new KnowledgeBaseService.EvalSuiteCommand(request.name(), request.description())));
    }

    @GetMapping("/eval/suites/{suiteId}/cases")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listEvalCases(@PathVariable Long suiteId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listEvalCases(orgId, suiteId));
    }

    @PostMapping("/eval/suites/{suiteId}/cases")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> addEvalCase(@PathVariable Long suiteId,
                                                         @Valid @RequestBody EvalCaseRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.addEvalCase(
                orgId,
                suiteId,
                new KnowledgeBaseService.EvalCaseCommand(
                        request.query(),
                        request.expectedDocumentId(),
                        request.expectedDocumentKeyword(),
                        request.expectedChunkKeyword(),
                        request.minScore(),
                        request.forbiddenDocumentId(),
                        request.metadataFilters())));
    }

    @PostMapping("/eval/suites/{suiteId}/runs")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> runEvalSuite(@PathVariable Long suiteId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.runEvalSuite(orgId, suiteId));
    }

    @GetMapping("/eval/suites/{suiteId}/runs")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listEvalRuns(@PathVariable Long suiteId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listEvalRuns(orgId, suiteId));
    }

    @GetMapping("/eval/runs/{runId}/results")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listEvalRunResults(@PathVariable Long runId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listEvalRunResults(orgId, runId));
    }

    @GetMapping("/{kbId}/metadata/fields")
    public ApiResponse<List<Map<String, Object>>> listMetadataFields(@PathVariable Long kbId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listMetadataFields(orgId, kbId));
    }

    @PostMapping("/{kbId}/quality/runs")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> startQualityScan(@PathVariable Long kbId,
                                                              @RequestBody(required = false) QualityScanRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.startScan(
                orgId,
                kbId,
                currentUserId(),
                new KbDataQualityService.QualityScanCommand(request == null ? "MANUAL" : request.triggerType())));
    }

    @GetMapping("/{kbId}/quality/runs")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listQualityRuns(@PathVariable Long kbId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.listRuns(orgId, kbId));
    }

    @GetMapping("/{kbId}/quality/issues")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listQualityIssues(@PathVariable Long kbId,
                                                                     @RequestParam(name = "status", required = false) String status) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.listIssues(orgId, kbId, status));
    }

    @PostMapping("/quality/issues/{issueId}/ignore")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> ignoreQualityIssue(@PathVariable Long issueId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.markIssue(orgId, issueId, currentUserId(), "IGNORED"));
    }

    @PostMapping("/quality/issues/{issueId}/resolve")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> resolveQualityIssue(@PathVariable Long issueId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.markIssue(orgId, issueId, currentUserId(), "RESOLVED"));
    }

    @GetMapping("/{kbId}/quality/rules")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listQualityRules(@PathVariable Long kbId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.listRules(orgId, kbId));
    }

    @PostMapping("/{kbId}/quality/rules")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> createQualityRule(@PathVariable Long kbId,
                                                               @Valid @RequestBody QualityRuleRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.createRule(
                orgId,
                kbId,
                currentUserId(),
                new KbDataQualityService.QualityRuleCommand(
                        request.name(),
                        request.ruleType(),
                        request.pattern(),
                        request.replacement(),
                        request.enabled())));
    }

    @PutMapping("/quality/rules/{ruleId}")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> updateQualityRule(@PathVariable Long ruleId,
                                                               @Valid @RequestBody QualityRuleRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.updateRule(
                orgId,
                ruleId,
                currentUserId(),
                new KbDataQualityService.QualityRuleCommand(
                        request.name(),
                        request.ruleType(),
                        request.pattern(),
                        request.replacement(),
                        request.enabled())));
    }

    @PostMapping("/quality/rules/{ruleId}/preview")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> previewQualityRule(@PathVariable Long ruleId,
                                                                @RequestBody(required = false) QualityApplyRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.previewRule(orgId, ruleId, toQualityApplyCommand(request)));
    }

    @PostMapping("/quality/rules/{ruleId}/apply")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> applyQualityRule(@PathVariable Long ruleId,
                                                              @RequestBody(required = false) QualityApplyRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.applyRule(
                orgId,
                ruleId,
                currentUserId(),
                toQualityApplyCommand(request)));
    }

    @PostMapping("/{kbId}/quality/annotations/suggest")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> suggestAnnotations(@PathVariable Long kbId,
                                                                @RequestBody(required = false) AnnotationSuggestRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.suggestAnnotations(
                orgId,
                kbId,
                currentUserId(),
                new KbDataQualityService.AnnotationSuggestCommand(
                        request == null ? null : request.targetType(),
                        request == null ? null : request.fieldKey(),
                        request == null ? null : request.limit())));
    }

    @GetMapping("/{kbId}/quality/annotations/suggestions")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listAnnotationSuggestions(@PathVariable Long kbId,
                                                                             @RequestParam(name = "status", required = false) String status) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.listSuggestions(orgId, kbId, status));
    }

    @PostMapping("/quality/annotations/suggestions/{suggestionId}/accept")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> acceptAnnotationSuggestion(@PathVariable Long suggestionId,
                                                                        @RequestBody(required = false) AnnotationReviewRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.acceptSuggestion(
                orgId,
                suggestionId,
                currentUserId(),
                new KbDataQualityService.AnnotationReviewCommand(request == null ? null : request.value())));
    }

    @PostMapping("/quality/annotations/suggestions/{suggestionId}/reject")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> rejectAnnotationSuggestion(@PathVariable Long suggestionId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.rejectSuggestion(orgId, suggestionId, currentUserId()));
    }

    @GetMapping("/{kbId}/quality/annotations/chunks")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listChunkAnnotations(@PathVariable Long kbId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(kbDataQualityService.listChunkAnnotations(orgId, kbId));
    }

    @PostMapping("/{kbId}/metadata/fields")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> createMetadataField(@PathVariable Long kbId,
                                                                 @Valid @RequestBody CreateMetadataFieldRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.createMetadataField(orgId, kbId, new KnowledgeBaseService.MetadataFieldCommand(
                request.fieldKey(),
                request.fieldName(),
                request.valueType()
        )));
    }

    @GetMapping("/documents/{id}/metadata")
    public ApiResponse<List<Map<String, Object>>> getDocumentMetadata(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.getDocumentMetadata(orgId, id));
    }

    @PutMapping("/documents/{id}/metadata")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> updateDocumentMetadata(@PathVariable Long id,
                                                                          @RequestBody Map<String, String> metadata) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.updateDocumentMetadata(orgId, id, metadata));
    }

    @GetMapping("/documents/{id}/acl")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listDocumentAccessGrants(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listDocumentAccessGrants(orgId, id));
    }

    @PutMapping("/documents/{id}/acl")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> replaceDocumentAccessGrants(@PathVariable Long id,
                                                                               @Valid @RequestBody ReplaceAccessGrantsRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.replaceDocumentAccessGrants(
                orgId,
                id,
                currentUserId(),
                toGrantInputs(request)));
    }

    @PostMapping("/documents/{id}/publish")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> publishDocument(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.publishDocument(orgId, id));
    }

    @PostMapping("/documents/{id}/reindex")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> reindexDocument(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.reindexDocument(orgId, id));
    }

    @PostMapping("/documents/{id}/unpublish")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> unpublishDocument(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.unpublishDocument(orgId, id));
    }

    @DeleteMapping("/documents/{id}")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> deleteDocument(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.deleteDocument(orgId, id));
    }

    @PostMapping("/{kbId}/chunks")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> addChunk(@PathVariable String kbId, @Valid @RequestBody AddChunkRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.addChunk(orgId, kbId, request.content(), request.tags()));
    }

    @PutMapping("/chunks/{id}")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> updateChunk(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateChunkRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.updateChunk(orgId, id, request.content()));
    }

    @PostMapping("/chunks/{id}/enable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> enableChunk(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.setChunkEnabled(orgId, id, true));
    }

    @GetMapping("/chunks/{id}/acl")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listChunkAccessGrants(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listChunkAccessGrants(orgId, id));
    }

    @PutMapping("/chunks/{id}/acl")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> replaceChunkAccessGrants(@PathVariable Long id,
                                                                            @Valid @RequestBody ReplaceAccessGrantsRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.replaceChunkAccessGrants(
                orgId,
                id,
                currentUserId(),
                toGrantInputs(request)));
    }

    @PostMapping("/chunks/{id}/disable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> disableChunk(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.setChunkEnabled(orgId, id, false));
    }

    @DeleteMapping("/chunks/{id}")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> deleteChunk(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.deleteChunk(orgId, id));
    }

    @PostMapping("/chunks/batch/enable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchEnableChunks(@Valid @RequestBody BatchIdsRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.batchSetChunkEnabled(orgId, request.ids(), true));
    }

    @PostMapping("/chunks/batch/disable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchDisableChunks(@Valid @RequestBody BatchIdsRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.batchSetChunkEnabled(orgId, request.ids(), false));
    }

    @PostMapping("/chunks/batch/delete")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchDeleteChunks(@Valid @RequestBody BatchIdsRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.batchDeleteChunks(orgId, request.ids()));
    }

    public record CreateKnowledgeBaseRequest(
            @NotBlank String name,
            String description
    ) {
    }

    public record AddChunkRequest(
            @NotBlank String content,
            String tags
    ) {
    }

    public record RenameDocumentRequest(
            @NotBlank String name
    ) {
    }

    public record UpdateChunkRequest(
            @NotBlank String content
    ) {
    }

    public record UpdateKnowledgeBaseSettingsRequest(
            Integer chunkSize,
            Integer chunkOverlap,
            String chunkDelimiter,
            String retrievalStrategy,
            Integer topK,
            Double scoreThreshold,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension
    ) {
    }

    public record ChunkPreviewRequest(
            @NotBlank String text,
            Integer chunkSize,
            Integer chunkOverlap,
            String chunkDelimiter,
            Integer maxChunks
    ) {
    }

    public record RetrievalTestRequest(
            @NotBlank String query,
            Integer topK,
            Double scoreThreshold,
            String retrievalStrategy,
            Map<String, String> metadataFilters
    ) {
    }

    public record DataSourceRequest(
            @NotBlank String sourceType,
            @NotBlank String name,
            Map<String, Object> config
    ) {
    }

    public record SyncDataSourceRequest(
            String triggerType
    ) {
    }

    public record EvalSuiteRequest(
            @NotBlank String name,
            String description
    ) {
    }

    public record EvalCaseRequest(
            @NotBlank String query,
            Long expectedDocumentId,
            String expectedDocumentKeyword,
            String expectedChunkKeyword,
            Double minScore,
            Long forbiddenDocumentId,
            Map<String, String> metadataFilters
    ) {
    }

    public record CreateMetadataFieldRequest(
            @NotBlank String fieldKey,
            String fieldName,
            String valueType
    ) {
    }

    public record QualityScanRequest(
            String triggerType
    ) {
    }

    public record QualityRuleRequest(
            @NotBlank String name,
            @NotBlank String ruleType,
            String pattern,
            String replacement,
            Boolean enabled
    ) {
    }

    public record QualityApplyRequest(
            List<Long> chunkIds,
            List<Long> issueIds,
            Map<Long, String> expectedContentHashes,
            Integer limit
    ) {
    }

    public record AnnotationSuggestRequest(
            String targetType,
            String fieldKey,
            Integer limit
    ) {
    }

    public record AnnotationReviewRequest(
            String value
    ) {
    }

    public record BatchIdsRequest(
            @NotEmpty List<Long> ids
    ) {
    }

    public record AccessGrantRequest(
            @NotBlank String principalType,
            String principalId,
            Instant expiresAt
    ) {
    }

    public record ReplaceAccessGrantsRequest(
            List<AccessGrantRequest> grants
    ) {
    }

    public record DriftAuditRequest(
            Boolean repair
    ) {
    }

    private List<KbAccessControlService.GrantInput> toGrantInputs(ReplaceAccessGrantsRequest request) {
        if (request == null || request.grants() == null || request.grants().isEmpty()) {
            return List.of();
        }
        return request.grants().stream()
                .map(item -> new KbAccessControlService.GrantInput(
                        item.principalType(),
                        item.principalId(),
                        item.expiresAt()))
                .toList();
    }

    private KbDataQualityService.QualityApplyCommand toQualityApplyCommand(QualityApplyRequest request) {
        return new KbDataQualityService.QualityApplyCommand(
                request == null ? List.of() : request.chunkIds(),
                request == null ? List.of() : request.issueIds(),
                request == null ? Map.of() : request.expectedContentHashes(),
                request == null ? null : request.limit());
    }

    private String currentUserId() {
        return TenantContext.getUserId().orElse("org-admin");
    }
}
