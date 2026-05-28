package com.codehouse.ciciassistant.openapi.api;

import com.codehouse.ciciassistant.openapi.service.AgentOpenApiConversationService;
import com.codehouse.ciciassistant.openapi.service.AgentOpenApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/openapi/v1")
public class AgentOpenApiController {

    private final AgentOpenApiConversationService conversationService;

    public AgentOpenApiController(AgentOpenApiConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/parameters")
    public ResponseEntity<Map<String, Object>> parameters(HttpServletRequest request) {
        String requestId = requestId();
        try {
            return ResponseEntity.ok(success(conversationService.parameters(request)));
        } catch (AgentOpenApiException ex) {
            return ResponseEntity.status(ex.getStatus()).body(error(ex, requestId));
        }
    }

    @PostMapping(value = "/chat-messages", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Object chatMessages(@RequestBody(required = false) AgentOpenApiConversationService.ChatMessageCommand requestBody,
                               HttpServletRequest request) {
        String requestId = requestId();
        String responseMode = requestBody == null ? "" : text(requestBody.responseMode()).toLowerCase(Locale.ROOT);
        try {
            if ("streaming".equals(responseMode)) {
                SseEmitter emitter = conversationService.chatMessagesStream(
                        requestId,
                        header(request, "Idempotency-Key"),
                        requestBody,
                        request);
                return emitter;
            }
            return ResponseEntity.ok(conversationService.chatMessages(
                    requestId,
                    header(request, "Idempotency-Key"),
                    requestBody,
                    request));
        } catch (AgentOpenApiException ex) {
            return ResponseEntity.status(ex.getStatus()).body(error(ex, requestId));
        }
    }

    @PostMapping("/chat-messages/{taskId}/stop")
    public ResponseEntity<Map<String, Object>> stop(@PathVariable String taskId,
                                                    HttpServletRequest request) {
        String requestId = requestId();
        try {
            return ResponseEntity.ok(conversationService.stop(taskId, request));
        } catch (AgentOpenApiException ex) {
            return ResponseEntity.status(ex.getStatus()).body(error(ex, requestId));
        }
    }

    @GetMapping("/conversations")
    public ResponseEntity<Map<String, Object>> conversations(@RequestParam(required = false) String user,
                                                             HttpServletRequest request) {
        String requestId = requestId();
        try {
            return ResponseEntity.ok(success(Map.of("data", conversationService.conversations(user, request))));
        } catch (AgentOpenApiException ex) {
            return ResponseEntity.status(ex.getStatus()).body(error(ex, requestId));
        }
    }

    @GetMapping("/messages")
    public ResponseEntity<Map<String, Object>> messages(@RequestParam(required = false, name = "conversation_id") String conversationId,
                                                        @RequestParam(required = false) String user,
                                                        @RequestParam(required = false, name = "first_id") String firstId,
                                                        @RequestParam(required = false) Integer limit,
                                                        HttpServletRequest request) {
        String requestId = requestId();
        try {
            AgentOpenApiConversationService.MessagePage page = conversationService.messages(conversationId, user, firstId, limit, request);
            return ResponseEntity.ok(success(Map.of("data", page.data(), "has_more", page.hasMore(), "limit", page.limit())));
        } catch (AgentOpenApiException ex) {
            return ResponseEntity.status(ex.getStatus()).body(error(ex, requestId));
        }
    }

    @PostMapping("/conversations/{conversationId}/name")
    public ResponseEntity<Map<String, Object>> renameConversation(@PathVariable String conversationId,
                                                                  @RequestBody(required = false) AgentOpenApiConversationService.RenameConversationCommand requestBody,
                                                                  HttpServletRequest request) {
        String requestId = requestId();
        try {
            return ResponseEntity.ok(conversationService.renameConversation(conversationId, requestBody, request));
        } catch (AgentOpenApiException ex) {
            return ResponseEntity.status(ex.getStatus()).body(error(ex, requestId));
        }
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Map<String, Object>> deleteConversation(@PathVariable String conversationId,
                                                                  HttpServletRequest request) {
        String requestId = requestId();
        try {
            return ResponseEntity.ok(conversationService.deleteConversation(conversationId, request));
        } catch (AgentOpenApiException ex) {
            return ResponseEntity.status(ex.getStatus()).body(error(ex, requestId));
        }
    }

    @PostMapping("/messages/{messageId}/feedbacks")
    public ResponseEntity<Map<String, Object>> feedback(@PathVariable String messageId,
                                                        @RequestBody(required = false) AgentOpenApiConversationService.FeedbackCommand requestBody,
                                                        HttpServletRequest request) {
        String requestId = requestId();
        try {
            return ResponseEntity.ok(conversationService.feedback(messageId, requestBody, request));
        } catch (AgentOpenApiException ex) {
            return ResponseEntity.status(ex.getStatus()).body(error(ex, requestId));
        }
    }

    @GetMapping("/messages/{messageId}/suggested")
    public ResponseEntity<Map<String, Object>> suggested(@PathVariable String messageId,
                                                         HttpServletRequest request) {
        String requestId = requestId();
        try {
            return ResponseEntity.ok(conversationService.suggested(messageId, request));
        } catch (AgentOpenApiException ex) {
            return ResponseEntity.status(ex.getStatus()).body(error(ex, requestId));
        }
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestPart("file") MultipartFile file,
                                                          @RequestParam(required = false) String user,
                                                          @RequestParam(required = false, name = "conversation_id") String conversationId,
                                                          HttpServletRequest request) {
        String requestId = requestId();
        try {
            return ResponseEntity.ok(conversationService.uploadFile(file, user, conversationId, request));
        } catch (AgentOpenApiException ex) {
            return ResponseEntity.status(ex.getStatus()).body(error(ex, requestId));
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

    private String text(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
