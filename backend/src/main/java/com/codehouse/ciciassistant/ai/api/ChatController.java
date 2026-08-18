package com.codehouse.ciciassistant.ai.api;

import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ai")
public class ChatController {

    private final ChatOrchestratorService chatOrchestratorService;

    public ChatController(ChatOrchestratorService chatOrchestratorService) {
        this.chatOrchestratorService = chatOrchestratorService;
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@Valid @RequestBody ChatRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(chatOrchestratorService.chatWeb(
                companyId,
                userId,
                request.sessionId(),
                request.question(),
                request.knowledgeBaseIds(),
                request.agentId(),
                request.activeSkillCode(),
                request.metadataFilters(),
                request.attachmentIds()
        ));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        SseEmitter emitter = new SseEmitter(600_000L);
        chatOrchestratorService.chatStreamWeb(
                companyId,
                userId,
                request.sessionId(),
                request.question(),
                request.knowledgeBaseIds(),
                request.agentId(),
                request.activeSkillCode(),
                request.metadataFilters(),
                request.attachmentIds(),
                emitter);
        return emitter;
    }

    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions() {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(chatOrchestratorService.sessions(companyId, userId));
    }

    @PostMapping("/sessions")
    public ApiResponse<Map<String, Object>> createSession(@Valid @RequestBody CreateSessionRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(chatOrchestratorService.createWebSession(companyId, userId, request.agentId()));
    }

    @GetMapping(value = "/sessions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sessionStream() {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return chatOrchestratorService.sessionStream(companyId, userId);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<Map<String, Object>>> sessionMessages(@NotBlank @PathVariable String sessionId) {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(chatOrchestratorService.sessionMessages(companyId, userId, sessionId));
    }

    @GetMapping("/sessions/{sessionId}/state")
    public ApiResponse<Map<String, Object>> sessionState(@NotBlank @PathVariable String sessionId) {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(chatOrchestratorService.sessionState(companyId, userId, sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Map<String, Object>> deleteSession(@NotBlank @PathVariable String sessionId) {
        String companyId = TenantContext.requireCompanyId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(chatOrchestratorService.deleteSession(companyId, userId, sessionId));
    }

    public record ChatRequest(
            @NotBlank String sessionId,
            String question,
            List<String> knowledgeBaseIds,
            String agentId,
            /** Normalized skill code to authorize skill-only tools for this session (optional). Empty clears persisted selection. */
            String activeSkillCode,
            Map<String, String> metadataFilters,
            List<String> attachmentIds
    ) {
    }

    public record CreateSessionRequest(@NotBlank String agentId) {
    }
}
