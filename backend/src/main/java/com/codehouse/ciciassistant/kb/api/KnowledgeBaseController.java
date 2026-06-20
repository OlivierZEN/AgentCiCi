package com.codehouse.ciciassistant.kb.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.kb.service.KbAccessControlService;
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

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
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

    @GetMapping("/{kbId}/metadata/fields")
    public ApiResponse<List<Map<String, Object>>> listMetadataFields(@PathVariable Long kbId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.listMetadataFields(orgId, kbId));
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

    public record CreateMetadataFieldRequest(
            @NotBlank String fieldKey,
            String fieldName,
            String valueType
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

    private String currentUserId() {
        return TenantContext.getUserId().orElse("org-admin");
    }
}
