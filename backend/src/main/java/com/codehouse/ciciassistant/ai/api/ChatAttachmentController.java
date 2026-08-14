package com.codehouse.ciciassistant.ai.api;

import com.codehouse.ciciassistant.ai.service.ChatAttachmentService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/ai/sessions/{sessionId}/attachments")
public class ChatAttachmentController {

    private final ChatAttachmentService service;

    public ChatAttachmentController(ChatAttachmentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(@PathVariable String sessionId,
                                                   @RequestParam String clientAttachmentId,
                                                   @RequestParam MultipartFile file) {
        return ApiResponse.ok(service.upload(companyId(), userId(), sessionId, clientAttachmentId, file));
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@PathVariable String sessionId) {
        return ApiResponse.ok(service.list(companyId(), userId(), sessionId));
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<?> content(@PathVariable String sessionId, @PathVariable String attachmentId) {
        ChatAttachmentService.AttachmentContent content = service.content(companyId(), userId(), sessionId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(content.filename()).build().toString())
                .body(content.resource());
    }

    @DeleteMapping("/{attachmentId}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String sessionId, @PathVariable String attachmentId) {
        return ApiResponse.ok(service.delete(companyId(), userId(), sessionId, attachmentId));
    }

    private static String companyId() {
        return TenantContext.requireCompanyId();
    }

    private static String userId() {
        return TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
    }
}
