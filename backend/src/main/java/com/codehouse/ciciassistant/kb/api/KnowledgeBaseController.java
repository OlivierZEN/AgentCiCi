package com.codehouse.ciciassistant.kb.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.kb.domain.KbChunkEntity;
import com.codehouse.ciciassistant.kb.domain.KbChunkRepository;
import com.codehouse.ciciassistant.kb.service.KnowledgeBaseService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    private final KbChunkRepository kbChunkRepository;
    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KbChunkRepository kbChunkRepository, KnowledgeBaseService knowledgeBaseService) {
        this.kbChunkRepository = kbChunkRepository;
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

    @PostMapping("/documents/upload")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> uploadDocument(@RequestParam("knowledgeBaseId") Long knowledgeBaseId,
                                                           @RequestParam("file") MultipartFile file) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.uploadDocument(orgId, knowledgeBaseId, file));
    }

    @PostMapping("/documents/{id}/publish")
    @RequireOrgAdmin
    public ApiResponse<Map<String, Object>> publishDocument(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(knowledgeBaseService.publishDocument(orgId, id));
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
        kbChunkRepository.save(new KbChunkEntity(orgId, kbId, request.content(), request.tags()));
        return ApiResponse.ok(Map.of(
                "orgId", orgId,
                "knowledgeBaseId", kbId,
                "status", "INDEXED"
        ));
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
}
