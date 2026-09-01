package com.codehouse.ciciassistant.embed.api;

import com.codehouse.ciciassistant.ai.service.ChatAttachmentService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.embed.service.EmbedTokenService;
import com.codehouse.ciciassistant.embed.service.SisiEmbedRuntimeService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/embed/v1/apps/sisi/sessions/{sessionId}")
public class SisiEmbedController {

    private final EmbedTokenService tokenService;
    private final SisiEmbedRuntimeService runtimeService;

    public SisiEmbedController(EmbedTokenService tokenService, SisiEmbedRuntimeService runtimeService) {
        this.tokenService = tokenService;
        this.runtimeService = runtimeService;
    }

    @GetMapping("/messages")
    public ApiResponse<Map<String, Object>> messages(@PathVariable String sessionId, HttpServletRequest request) {
        return ApiResponse.ok(runtimeService.messages(token(request), sessionId));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String sessionId,
                             @RequestBody SisiEmbedRuntimeService.ChatCommand command,
                             HttpServletRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        runtimeService.stream(token(request), sessionId, command, emitter);
        return emitter;
    }

    @PostMapping("/website/visit-choice")
    public ApiResponse<Map<String, Object>> chooseVisit(
            @PathVariable String sessionId,
            @RequestBody SisiEmbedRuntimeService.VisitChoiceCommand command,
            HttpServletRequest request) {
        return ApiResponse.ok(runtimeService.chooseVisit(token(request), sessionId, command));
    }

    @GetMapping("/website/ticket-entry")
    public ApiResponse<Map<String, Object>> ticketEntry(@PathVariable String sessionId, HttpServletRequest request) {
        return ApiResponse.ok(runtimeService.ticketEntry(token(request), sessionId));
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(@PathVariable String sessionId,
                                                   @RequestParam String clientAttachmentId,
                                                   @RequestParam MultipartFile file,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(runtimeService.uploadAttachment(
                token(request), sessionId, clientAttachmentId, file));
    }

    @GetMapping("/attachments")
    public ApiResponse<Map<String, Object>> attachments(@PathVariable String sessionId, HttpServletRequest request) {
        return ApiResponse.ok(runtimeService.listAttachments(token(request), sessionId));
    }

    @GetMapping("/attachments/{attachmentId}/content")
    public ResponseEntity<?> content(@PathVariable String sessionId,
                                     @PathVariable String attachmentId,
                                     HttpServletRequest request) {
        ChatAttachmentService.AttachmentContent content = runtimeService.attachmentContent(
                token(request), sessionId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.filename()).build().toString())
                .body(content.resource());
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String sessionId,
                                                   @PathVariable String attachmentId,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(runtimeService.deleteAttachment(token(request), sessionId, attachmentId));
    }

    private EmbedTokenService.AuthenticatedEmbedToken token(HttpServletRequest request) {
        return tokenService.authenticateEmbedToken("sisi", request);
    }
}
