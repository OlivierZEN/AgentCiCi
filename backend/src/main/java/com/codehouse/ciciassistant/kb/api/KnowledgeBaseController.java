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
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.createKnowledgeBase(companyId, request.name(), request.description()));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listKnowledgeBases() {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.listKnowledgeBases(companyId));
    }

    @PutMapping("/{id}")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> updateKnowledgeBase(@PathVariable Long id,
                                                                @Valid @RequestBody CreateKnowledgeBaseRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.updateKnowledgeBase(companyId, id, request.name(), request.description()));
    }

    @GetMapping("/{id}/settings")
    public ApiResponse<Map<String, Object>> getKnowledgeBaseSettings(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.getKnowledgeBaseSettings(companyId, id));
    }

    @PutMapping("/{id}/settings")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> updateKnowledgeBaseSettings(@PathVariable Long id,
                                                                         @Valid @RequestBody UpdateKnowledgeBaseSettingsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.updateKnowledgeBaseSettings(companyId, id, new KnowledgeBaseService.KbSettingsCommand(
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
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.listEmbeddingModelOptions(companyId));
    }

    @GetMapping("/upload-policy")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> uploadPolicy() {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.uploadPolicy(companyId));
    }

    @GetMapping("/vector-store/audit")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> auditVectorStore() {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.auditVectorStore(companyId));
    }

    @PostMapping("/drift/audit")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> auditIndexDrift(@RequestBody(required = false) DriftAuditRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.auditIndexDrift(companyId, request != null && Boolean.TRUE.equals(request.repair())));
    }

    @DeleteMapping("/{id}")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> deleteKnowledgeBase(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.deleteKnowledgeBase(companyId, id));
    }

    @GetMapping("/{kbId}/documents")
    public ApiResponse<List<Map<String, Object>>> listDocuments(@PathVariable Long kbId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.listDocuments(companyId, kbId));
    }

    @PutMapping("/documents/{id}/rename")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> renameDocument(@PathVariable Long id,
                                                            @Valid @RequestBody RenameDocumentRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.renameDocument(companyId, id, request.name()));
    }

    @PostMapping("/documents/{id}/enable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> enableDocument(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.setDocumentEnabled(companyId, id, true));
    }

    @PostMapping("/documents/{id}/disable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> disableDocument(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.setDocumentEnabled(companyId, id, false));
    }

    @PostMapping("/documents/{id}/archive")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> archiveDocument(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.setDocumentArchived(companyId, id, true));
    }

    @PostMapping("/documents/{id}/unarchive")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> unarchiveDocument(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.setDocumentArchived(companyId, id, false));
    }

    @PostMapping("/documents/batch/enable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchEnableDocuments(@Valid @RequestBody BatchIdsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.batchSetDocumentEnabled(companyId, request.ids(), true));
    }

    @PostMapping("/documents/batch/disable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchDisableDocuments(@Valid @RequestBody BatchIdsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.batchSetDocumentEnabled(companyId, request.ids(), false));
    }

    @PostMapping("/documents/batch/archive")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchArchiveDocuments(@Valid @RequestBody BatchIdsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.batchSetDocumentArchived(companyId, request.ids(), true));
    }

    @PostMapping("/documents/batch/unarchive")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchUnarchiveDocuments(@Valid @RequestBody BatchIdsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.batchSetDocumentArchived(companyId, request.ids(), false));
    }

    @PostMapping("/documents/batch/delete")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchDeleteDocuments(@Valid @RequestBody BatchIdsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.batchDeleteDocuments(companyId, request.ids()));
    }

    @GetMapping("/documents/{id}/chunks")
    public ApiResponse<List<Map<String, Object>>> listDocumentChunks(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.listDocumentChunks(companyId, id));
    }

    @PostMapping("/documents/upload")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> uploadDocument(@RequestParam("knowledgeBaseId") Long knowledgeBaseId,
                                                           @RequestParam("file") MultipartFile file) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.uploadDocument(companyId, knowledgeBaseId, file));
    }

    @GetMapping("/{kbId}/data-sources")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listDataSources(@PathVariable Long kbId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.listDataSources(companyId, kbId));
    }

    @PostMapping("/{kbId}/data-sources")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> createDataSource(@PathVariable Long kbId,
                                                              @Valid @RequestBody DataSourceRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.createDataSource(
                companyId,
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
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.syncDataSource(
                companyId,
                sourceId,
                request == null ? "MANUAL" : request.triggerType()));
    }

    @GetMapping("/data-sources/{sourceId}/sync-jobs")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listSyncJobs(@PathVariable Long sourceId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.listSyncJobs(companyId, sourceId));
    }

    @PostMapping("/{kbId}/chunking/preview")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> previewChunking(@PathVariable Long kbId,
                                                             @Valid @RequestBody ChunkPreviewRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.previewChunking(companyId, kbId, new KnowledgeBaseService.ChunkPreviewCommand(
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
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.testRetrieval(companyId, kbId, new KnowledgeBaseService.RetrievalTestCommand(
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
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.listRetrievalLogs(companyId, kbId, limit));
    }

    @GetMapping("/{kbId}/eval/suites")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listEvalSuites(@PathVariable Long kbId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.listEvalSuites(companyId, kbId));
    }

    @PostMapping("/{kbId}/eval/suites")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> createEvalSuite(@PathVariable Long kbId,
                                                             @Valid @RequestBody EvalSuiteRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.createEvalSuite(
                companyId,
                kbId,
                new KnowledgeBaseService.EvalSuiteCommand(request.name(), request.description())));
    }

    @GetMapping("/eval/suites/{suiteId}/cases")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listEvalCases(@PathVariable Long suiteId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.listEvalCases(companyId, suiteId));
    }

    @PostMapping("/eval/suites/{suiteId}/cases")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> addEvalCase(@PathVariable Long suiteId,
                                                         @Valid @RequestBody EvalCaseRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.addEvalCase(
                companyId,
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
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.runEvalSuite(companyId, suiteId));
    }

    @GetMapping("/eval/suites/{suiteId}/runs")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listEvalRuns(@PathVariable Long suiteId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.listEvalRuns(companyId, suiteId));
    }

    @GetMapping("/eval/runs/{runId}/results")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listEvalRunResults(@PathVariable Long runId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.listEvalRunResults(companyId, runId));
    }

    @GetMapping("/{kbId}/metadata/fields")
    public ApiResponse<List<Map<String, Object>>> listMetadataFields(@PathVariable Long kbId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.listMetadataFields(companyId, kbId));
    }

    @PostMapping("/{kbId}/quality/runs")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> startQualityScan(@PathVariable Long kbId,
                                                              @RequestBody(required = false) QualityScanRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.startScan(
                companyId,
                kbId,
                currentUserId(),
                new KbDataQualityService.QualityScanCommand(request == null ? "MANUAL" : request.triggerType())));
    }

    @GetMapping("/{kbId}/quality/runs")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listQualityRuns(@PathVariable Long kbId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.listRuns(companyId, kbId));
    }

    @GetMapping("/{kbId}/quality/issues")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listQualityIssues(@PathVariable Long kbId,
                                                                     @RequestParam(name = "status", required = false) String status) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.listIssues(companyId, kbId, status));
    }

    @PostMapping("/quality/issues/{issueId}/ignore")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> ignoreQualityIssue(@PathVariable Long issueId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.markIssue(companyId, issueId, currentUserId(), "IGNORED"));
    }

    @PostMapping("/quality/issues/{issueId}/resolve")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> resolveQualityIssue(@PathVariable Long issueId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.markIssue(companyId, issueId, currentUserId(), "RESOLVED"));
    }

    @GetMapping("/{kbId}/quality/rules")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listQualityRules(@PathVariable Long kbId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.listRules(companyId, kbId));
    }

    @PostMapping("/{kbId}/quality/rules")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> createQualityRule(@PathVariable Long kbId,
                                                               @Valid @RequestBody QualityRuleRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.createRule(
                companyId,
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
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.updateRule(
                companyId,
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
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.previewRule(companyId, ruleId, toQualityApplyCommand(request)));
    }

    @PostMapping("/quality/rules/{ruleId}/apply")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> applyQualityRule(@PathVariable Long ruleId,
                                                              @RequestBody(required = false) QualityApplyRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.applyRule(
                companyId,
                ruleId,
                currentUserId(),
                toQualityApplyCommand(request)));
    }

    @PostMapping("/{kbId}/quality/annotations/suggest")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> suggestAnnotations(@PathVariable Long kbId,
                                                                @RequestBody(required = false) AnnotationSuggestRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.suggestAnnotations(
                companyId,
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
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.listSuggestions(companyId, kbId, status));
    }

    @PostMapping("/quality/annotations/suggestions/{suggestionId}/accept")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> acceptAnnotationSuggestion(@PathVariable Long suggestionId,
                                                                        @RequestBody(required = false) AnnotationReviewRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.acceptSuggestion(
                companyId,
                suggestionId,
                currentUserId(),
                new KbDataQualityService.AnnotationReviewCommand(request == null ? null : request.value())));
    }

    @PostMapping("/quality/annotations/suggestions/{suggestionId}/reject")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> rejectAnnotationSuggestion(@PathVariable Long suggestionId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.rejectSuggestion(companyId, suggestionId, currentUserId()));
    }

    @GetMapping("/{kbId}/quality/annotations/chunks")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listChunkAnnotations(@PathVariable Long kbId) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(kbDataQualityService.listChunkAnnotations(companyId, kbId));
    }

    @PostMapping("/{kbId}/metadata/fields")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> createMetadataField(@PathVariable Long kbId,
                                                                 @Valid @RequestBody CreateMetadataFieldRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.createMetadataField(companyId, kbId, new KnowledgeBaseService.MetadataFieldCommand(
                request.fieldKey(),
                request.fieldName(),
                request.valueType()
        )));
    }

    @GetMapping("/documents/{id}/metadata")
    public ApiResponse<List<Map<String, Object>>> getDocumentMetadata(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.getDocumentMetadata(companyId, id));
    }

    @PutMapping("/documents/{id}/metadata")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> updateDocumentMetadata(@PathVariable Long id,
                                                                          @RequestBody Map<String, String> metadata) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.updateDocumentMetadata(companyId, id, metadata));
    }

    @GetMapping("/documents/{id}/acl")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listDocumentAccessGrants(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.listDocumentAccessGrants(companyId, id));
    }

    @PutMapping("/documents/{id}/acl")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> replaceDocumentAccessGrants(@PathVariable Long id,
                                                                               @Valid @RequestBody ReplaceAccessGrantsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.replaceDocumentAccessGrants(
                companyId,
                id,
                currentUserId(),
                toGrantInputs(request)));
    }

    @PostMapping("/documents/{id}/publish")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> publishDocument(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.publishDocument(companyId, id));
    }

    @PostMapping("/documents/{id}/reindex")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> reindexDocument(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.reindexDocument(companyId, id));
    }

    @PostMapping("/documents/{id}/unpublish")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> unpublishDocument(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.unpublishDocument(companyId, id));
    }

    @DeleteMapping("/documents/{id}")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> deleteDocument(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.deleteDocument(companyId, id));
    }

    @PostMapping("/{kbId}/chunks")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> addChunk(@PathVariable String kbId, @Valid @RequestBody AddChunkRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.addChunk(companyId, kbId, request.content(), request.tags()));
    }

    @PutMapping("/chunks/{id}")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> updateChunk(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateChunkRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.updateChunk(companyId, id, request.content()));
    }

    @PostMapping("/chunks/{id}/enable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> enableChunk(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.setChunkEnabled(companyId, id, true));
    }

    @GetMapping("/chunks/{id}/acl")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> listChunkAccessGrants(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.listChunkAccessGrants(companyId, id));
    }

    @PutMapping("/chunks/{id}/acl")
    @RequireOrgAdmin
    public ApiResponse<List<Map<String, Object>>> replaceChunkAccessGrants(@PathVariable Long id,
                                                                            @Valid @RequestBody ReplaceAccessGrantsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.replaceChunkAccessGrants(
                companyId,
                id,
                currentUserId(),
                toGrantInputs(request)));
    }

    @PostMapping("/chunks/{id}/disable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> disableChunk(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.setChunkEnabled(companyId, id, false));
    }

    @DeleteMapping("/chunks/{id}")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> deleteChunk(@PathVariable Long id) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.deleteChunk(companyId, id));
    }

    @PostMapping("/chunks/batch/enable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchEnableChunks(@Valid @RequestBody BatchIdsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.batchSetChunkEnabled(companyId, request.ids(), true));
    }

    @PostMapping("/chunks/batch/disable")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchDisableChunks(@Valid @RequestBody BatchIdsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.batchSetChunkEnabled(companyId, request.ids(), false));
    }

    @PostMapping("/chunks/batch/delete")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> batchDeleteChunks(@Valid @RequestBody BatchIdsRequest request) {
        String companyId = TenantContext.requireCompanyId();
        return ApiResponse.ok(knowledgeBaseService.batchDeleteChunks(companyId, request.ids()));
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
