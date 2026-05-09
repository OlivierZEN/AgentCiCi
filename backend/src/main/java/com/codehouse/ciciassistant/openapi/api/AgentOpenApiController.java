package com.codehouse.ciciassistant.openapi.api;

import com.codehouse.ciciassistant.openapi.service.AgentOpenApiAuthService;
import com.codehouse.ciciassistant.openapi.service.AgentOpenApiException;
import com.codehouse.ciciassistant.openapi.service.AgentOpenApiRunService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/openapi/v1/agents")
public class AgentOpenApiController {

    private final AgentOpenApiAuthService authService;
    private final AgentOpenApiRunService runService;

    public AgentOpenApiController(AgentOpenApiAuthService authService, AgentOpenApiRunService runService) {
        this.authService = authService;
        this.runService = runService;
    }

    @GetMapping("/{agentId}/health")
    public ResponseEntity<Map<String, Object>> health(@PathVariable String agentId, HttpServletRequest request) {
        String requestId = requestId();
        try {
            AgentOpenApiAuthService.AuthenticatedCredential auth = authService.authenticate(agentId, request);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("requestId", requestId);
            data.put("agentId", auth.agent().getAgentId());
            data.put("enabled", auth.agent().isEnabled());
            data.put("published", auth.agent().getPublishedVersionId() != null);
            data.put("apiChannelEnabled", true);
            data.put("credentialStatus", auth.credential().getStatus());
            data.put("serverTime", Instant.now().toString());
            return ResponseEntity.ok(success(data));
        } catch (AgentOpenApiException ex) {
            return ResponseEntity.status(ex.getStatus()).body(error(ex, requestId));
        }
    }

    @PostMapping("/{agentId}/chat")
    public ResponseEntity<Map<String, Object>> chat(@PathVariable String agentId,
                                                    @RequestBody(required = false) AgentOpenApiRunService.ChatCommand requestBody,
                                                    HttpServletRequest request) {
        String requestId = requestId();
        try {
            Map<String, Object> data = runService.chat(
                    agentId,
                    requestId,
                    header(request, "Idempotency-Key"),
                    requestBody,
                    request);
            return ResponseEntity.ok(success(data));
        } catch (AgentOpenApiException ex) {
            return ResponseEntity.status(ex.getStatus()).body(error(ex, requestId));
        }
    }

    @PostMapping(value = "/{agentId}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> chatStream(@PathVariable String agentId,
                                                 @RequestBody(required = false) AgentOpenApiRunService.ChatCommand requestBody,
                                                 HttpServletRequest request) {
        String requestId = requestId();
        try {
            SseEmitter emitter = runService.chatStream(
                    agentId,
                    requestId,
                    header(request, "Idempotency-Key"),
                    requestBody,
                    request);
            return ResponseEntity.ok(emitter);
        } catch (AgentOpenApiException ex) {
            SseEmitter emitter = new SseEmitter(10_000L);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of(
                                "requestId", requestId,
                                "code", ex.getCode(),
                                "message", ex.getMessage()
                        )));
                emitter.complete();
            } catch (IOException ignored) {
                emitter.completeWithError(ex);
            }
            return ResponseEntity.status(ex.getStatus()).body(emitter);
        }
    }

    private Map<String, Object> success(Map<String, Object> data) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("success", true);
        root.put("data", data);
        root.put("message", "OK");
        return root;
    }

    private Map<String, Object> error(AgentOpenApiException ex, String requestId) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("success", false);
        root.put("data", null);
        root.put("message", ex.getMessage());
        root.put("error", Map.of(
                "code", ex.getCode(),
                "requestId", requestId,
                "details", Map.of()
        ));
        return root;
    }

    private String requestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? "" : value.trim();
    }
}
