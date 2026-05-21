package com.codehouse.ciciassistant.openapi.service;

import com.codehouse.ciciassistant.openapi.config.AgentOpenApiProperties;
import com.codehouse.ciciassistant.openapi.domain.AgentApiFeedbackEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiFeedbackRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiFileEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiFileRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiMessageEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiMessageRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiSessionMapEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiSessionMapRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiTaskEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiTaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AgentOpenApiConversationService {

    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};
    private static final List<String> ALLOWED_DOCUMENT_MIME_TYPES = List.of(
            "text/plain",
            "text/markdown",
            "text/csv",
            "application/json",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final AgentOpenApiAuthService authService;
    private final AgentOpenApiRunService runService;
    private final AgentApiMessageRepository messageRepository;
    private final AgentApiTaskRepository taskRepository;
    private final AgentApiFeedbackRepository feedbackRepository;
    private final AgentApiFileRepository fileRepository;
    private final AgentApiSessionMapRepository sessionMapRepository;
    private final AgentOpenApiProperties properties;
    private final ObjectMapper objectMapper;

    public AgentOpenApiConversationService(AgentOpenApiAuthService authService,
                                           AgentOpenApiRunService runService,
                                           AgentApiMessageRepository messageRepository,
                                           AgentApiTaskRepository taskRepository,
                                           AgentApiFeedbackRepository feedbackRepository,
                                           AgentApiFileRepository fileRepository,
                                           AgentApiSessionMapRepository sessionMapRepository,
                                           AgentOpenApiProperties properties,
                                           ObjectMapper objectMapper) {
        this.authService = authService;
        this.runService = runService;
        this.messageRepository = messageRepository;
        this.taskRepository = taskRepository;
        this.feedbackRepository = feedbackRepository;
        this.fileRepository = fileRepository;
        this.sessionMapRepository = sessionMapRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> parameters(String agentId, HttpServletRequest request) {
        requireEnabled();
        AgentOpenApiAuthService.AuthenticatedCredential auth = authService.authenticate(agentId, request);
        authService.requireScope(auth, "chat");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("opening_statement", text(auth.agent().getGreeting()));
        data.put("suggested_questions", List.of("请介绍一下你能提供什么帮助", "帮我分析当前问题的下一步建议"));
        data.put("suggested_questions_after_answer", Map.of("enabled", true));
        data.put("file_upload", Map.of(
                "enabled", auth.credentialView().scopes().contains("files") || auth.credentialView().scopes().contains("*"),
                "number_limits", 5,
                "file_size_limit", 15,
                "allowed_file_types", List.of("document", "image")));
        data.put("system_parameters", Map.of(
                "max_prompt_chars", auth.credentialView().maxPromptChars(),
                "max_response_chars", auth.credentialView().maxResponseChars(),
                "streaming", auth.credentialView().allowStream()));
        data.put("retriever_resource", Map.of("enabled", true));
        data.put("user_input_form", List.of());
        data.put("tts", Map.of("enabled", false));
        data.put("speech_to_text", Map.of("enabled", false));
        return data;
    }

    public Map<String, Object> chatMessages(String agentId,
                                            String requestId,
                                            String idempotencyKey,
                                            ChatMessageCommand requestBody,
                                            HttpServletRequest request) {
        requireEnabled();
        AgentOpenApiAuthService.AuthenticatedCredential auth = authService.authenticate(agentId, request);
        authService.requireScope(auth, "chat");
        ChatMessageInput input = normalize(requestBody);
        validateFiles(auth, input.files(), input.externalUserId(), input.conversationId());
        String normalizedIdempotency = normalizeIdempotency(idempotencyKey);
        if (!normalizedIdempotency.isBlank()) {
            AgentApiMessageEntity existing = messageRepository
                    .findFirstByCredentialIdAndIdempotencyKeyAndStatusOrderByCreatedAtDesc(
                            auth.credential().getId(),
                            normalizedIdempotency,
                            AgentApiMessageEntity.STATUS_SUCCESS)
                    .orElse(null);
            if (existing != null) {
                return messagePayload(existing, Map.of("idempotentReplay", true));
            }
        }
        String taskId = id("task");
        AgentOpenApiRunService.ChatCommand command = new AgentOpenApiRunService.ChatCommand(
                input.conversationId(),
                input.query(),
                input.externalUser(),
                input.knowledgeBaseIds(),
                input.activeSkillCode(),
                input.metadata(),
                input.cloudccContext());
        AgentApiTaskEntity task = taskRepository.save(new AgentApiTaskEntity(
                taskId,
                requestId,
                auth.credential().getOrgId(),
                auth.credential().getId(),
                auth.credential().getAgentId(),
                input.externalUserId(),
                input.conversationId()));
        try {
            AgentOpenApiRunService.ChatExecution execution = runService.chatWithAuth(
                    auth,
                    requestId,
                    normalizedIdempotency,
                    command,
                    request,
                    Instant.now());
            task.completeSuccess();
            taskRepository.save(task);
            AgentApiMessageEntity message = saveMessage(
                    id("msg"),
                    requestId,
                    taskId,
                    execution,
                    input,
                    AgentApiMessageEntity.STATUS_SUCCESS,
                    "",
                    normalizedIdempotency);
            return messagePayload(message, Map.of(
                    "trace_id", execution.traceId(),
                    "usage", usage(execution.elapsedMs()),
                    "retriever_resources", List.of(),
                    "agent_thoughts", List.of(Map.of("thought", "AgentCiCi runtime completed", "observation", "completed"))));
        } catch (AgentOpenApiException ex) {
            task.completeFailure();
            taskRepository.save(task);
            throw ex;
        }
    }

    public SseEmitter chatMessagesStream(String agentId,
                                         String requestId,
                                         String idempotencyKey,
                                         ChatMessageCommand requestBody,
                                         HttpServletRequest request) {
        requireEnabled();
        SseEmitter emitter = new SseEmitter(600_000L);
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> data = chatMessages(agentId, requestId, idempotencyKey, requestBody, request);
                String answer = text(data.get("answer"));
                emitter.send(SseEmitter.event().name("agent_thought").data(Map.of(
                        "event", "agent_thought",
                        "task_id", data.get("task_id"),
                        "message_id", data.get("message_id"),
                        "thought", "AgentCiCi runtime completed")));
                emitter.send(SseEmitter.event().name("message").data(Map.of(
                        "event", "message",
                        "task_id", data.get("task_id"),
                        "message_id", data.get("message_id"),
                        "conversation_id", data.get("conversation_id"),
                        "answer", answer)));
                emitter.send(SseEmitter.event().name("message_end").data(Map.of(
                        "event", "message_end",
                        "task_id", data.get("task_id"),
                        "message_id", data.get("message_id"),
                        "conversation_id", data.get("conversation_id"),
                        "metadata", data.get("metadata"))));
                emitter.complete();
            } catch (AgentOpenApiException ex) {
                sendError(emitter, requestId, ex);
            } catch (Exception ex) {
                sendError(emitter, requestId, new AgentOpenApiException(HttpStatus.BAD_GATEWAY, "model_or_tool_failed", "Agent runtime failed"));
            }
        });
        return emitter;
    }

    public Map<String, Object> stop(String agentId, String taskId, HttpServletRequest request) {
        requireEnabled();
        AgentOpenApiAuthService.AuthenticatedCredential auth = authService.authenticate(agentId, request);
        authService.requireScope(auth, "chat");
        AgentApiTaskEntity task = taskRepository
                .findByTaskIdAndOrgIdAndCredentialIdAndAgentId(
                        taskId,
                        auth.credential().getOrgId(),
                        auth.credential().getId(),
                        auth.credential().getAgentId())
                .orElseThrow(() -> new AgentOpenApiException(HttpStatus.NOT_FOUND, "task_not_found", "Task not found"));
        task.markCancelRequested();
        taskRepository.save(task);
        return Map.of("result", "cancel_requested", "task_id", task.getTaskId(), "status", task.getStatus());
    }

    public List<Map<String, Object>> conversations(String agentId, String user, HttpServletRequest request) {
        requireEnabled();
        AgentOpenApiAuthService.AuthenticatedCredential auth = authService.authenticate(agentId, request);
        authService.requireScope(auth, "history");
        String normalizedUser = text(user);
        return sessionMapRepository
                .findTop100ByOrgIdAndCredentialIdAndAgentIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
                        auth.credential().getOrgId(),
                        auth.credential().getId(),
                        auth.credential().getAgentId())
                .stream()
                .filter(item -> normalizedUser.isBlank() || normalizedUser.equals(text(item.getExternalUserId())))
                .map(this::conversationPayload)
                .toList();
    }

    public Map<String, Object> renameConversation(String agentId,
                                                  String conversationId,
                                                  RenameConversationCommand command,
                                                  HttpServletRequest request) {
        requireEnabled();
        AgentOpenApiAuthService.AuthenticatedCredential auth = authService.authenticate(agentId, request);
        authService.requireScope(auth, "history");
        AgentApiSessionMapEntity session = requireConversation(auth, conversationId);
        session.rename(clip(command == null ? "" : command.name(), 160));
        sessionMapRepository.save(session);
        return conversationPayload(session);
    }

    public Map<String, Object> deleteConversation(String agentId, String conversationId, HttpServletRequest request) {
        requireEnabled();
        AgentOpenApiAuthService.AuthenticatedCredential auth = authService.authenticate(agentId, request);
        authService.requireScope(auth, "history");
        AgentApiSessionMapEntity session = requireConversation(auth, conversationId);
        session.markDeleted();
        sessionMapRepository.save(session);
        return Map.of("result", "deleted", "conversation_id", session.getExternalSessionId());
    }

    public MessagePage messages(String agentId,
                                String conversationId,
                                String user,
                                String firstId,
                                Integer limit,
                                HttpServletRequest request) {
        requireEnabled();
        AgentOpenApiAuthService.AuthenticatedCredential auth = authService.authenticate(agentId, request);
        authService.requireScope(auth, "history");
        List<AgentApiMessageEntity> rows = text(conversationId).isBlank()
                ? messageRepository.findTop100ByOrgIdAndCredentialIdAndAgentIdOrderByCreatedAtDesc(
                        auth.credential().getOrgId(),
                        auth.credential().getId(),
                        auth.credential().getAgentId())
                : messageRepository.findTop100ByOrgIdAndCredentialIdAndAgentIdAndExternalSessionIdOrderByCreatedAtDesc(
                        auth.credential().getOrgId(),
                        auth.credential().getId(),
                        auth.credential().getAgentId(),
                        text(conversationId));
        String normalizedUser = text(user);
        List<Map<String, Object>> candidates = rows.stream()
                .filter(item -> normalizedUser.isBlank() || normalizedUser.equals(text(item.getExternalUserId())))
                .map(item -> messagePayload(item, Map.of()))
                .toList();
        int normalizedLimit = normalizeLimit(limit);
        int start = pageStart(candidates, firstId);
        List<Map<String, Object>> paged = candidates.stream()
                .skip(start)
                .limit(normalizedLimit)
                .toList();
        return new MessagePage(paged, start + paged.size() < candidates.size(), normalizedLimit);
    }

    public Map<String, Object> feedback(String agentId,
                                        String messageId,
                                        FeedbackCommand command,
                                        HttpServletRequest request) {
        requireEnabled();
        AgentOpenApiAuthService.AuthenticatedCredential auth = authService.authenticate(agentId, request);
        authService.requireScope(auth, "feedback");
        AgentApiMessageEntity message = requireMessage(auth, messageId);
        String rating = normalizeRating(command == null ? "" : command.rating());
        AgentApiFeedbackEntity feedback = feedbackRepository.save(new AgentApiFeedbackEntity(
                message.getMessageId(),
                auth.credential().getOrgId(),
                auth.credential().getId(),
                auth.credential().getAgentId(),
                rating,
                clip(command == null ? "" : command.content(), 2000)));
        return Map.of(
                "result", "success",
                "message_id", message.getMessageId(),
                "rating", feedback.getRating(),
                "created_at", feedback.getCreatedAt().toString());
    }

    public Map<String, Object> suggested(String agentId, String messageId, HttpServletRequest request) {
        requireEnabled();
        AgentOpenApiAuthService.AuthenticatedCredential auth = authService.authenticate(agentId, request);
        authService.requireScope(auth, "feedback");
        AgentApiMessageEntity message = requireMessage(auth, messageId);
        return Map.of(
                "message_id", message.getMessageId(),
                "data", List.of("继续展开关键依据", "生成下一步行动清单", "用更短的话总结"));
    }

    public Map<String, Object> uploadFile(String agentId,
                                          MultipartFile file,
                                          String user,
                                          String conversationId,
                                          HttpServletRequest request) {
        requireEnabled();
        AgentOpenApiAuthService.AuthenticatedCredential auth = authService.authenticate(agentId, request);
        authService.requireScope(auth, "files");
        if (file == null || file.isEmpty()) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "invalid_request", "file is required");
        }
        if (file.getSize() > 15L * 1024L * 1024L) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "file_too_large", "file must be 15MB or smaller");
        }
        String mimeType = normalizeMimeType(file.getContentType(), file.getOriginalFilename());
        if (!isAllowedUploadMimeType(mimeType)) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "file_type_not_allowed", "file must be a document or image type");
        }
        String fileId = id("file");
        AgentApiFileEntity entity = fileRepository.save(new AgentApiFileEntity(
                fileId,
                auth.credential().getOrgId(),
                auth.credential().getId(),
                auth.credential().getAgentId(),
                text(user),
                text(conversationId),
                clip(file.getOriginalFilename(), 255).isBlank() ? fileId : clip(file.getOriginalFilename(), 255),
                file.getSize(),
                clip(mimeType, 128),
                "agent-open-api://" + auth.credential().getId() + "/" + fileId));
        return filePayload(entity);
    }

    private ChatMessageInput normalize(ChatMessageCommand requestBody) {
        if (requestBody == null) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "invalid_request", "Request body is required");
        }
        String query = firstText(requestBody.query(), requestBody.message());
        String user = text(requestBody.user());
        Map<String, Object> externalUser = requestBody.externalUser() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(requestBody.externalUser());
        String externalUserId = text(externalUser.get("id"));
        if (!user.isBlank() && !externalUserId.isBlank() && !user.equals(externalUserId)) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "invalid_request", "user must match externalUser.id when both are provided");
        }
        if (externalUserId.isBlank() && !user.isBlank()) {
            externalUser.put("id", user);
            externalUserId = user;
        }
        Map<String, Object> metadata = requestBody.metadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(requestBody.metadata());
        metadata.put("inputs", requestBody.inputs() == null ? Map.of() : requestBody.inputs());
        metadata.put("files", requestBody.files() == null ? List.of() : requestBody.files());
        return new ChatMessageInput(
                query,
                firstText(requestBody.conversationId(), requestBody.sessionId()),
                externalUserId,
                externalUser,
                requestBody.knowledgeBaseIds() == null ? List.of() : requestBody.knowledgeBaseIds(),
                text(requestBody.activeSkillCode()),
                metadata,
                requestBody.cloudccContext(),
                requestBody.files() == null ? List.of() : requestBody.files());
    }

    private void requireEnabled() {
        if (!properties.isConversationApiEnabled()) {
            throw new AgentOpenApiException(HttpStatus.FORBIDDEN, "agent_open_api_conversation_api_disabled", "Agent Open API conversation service endpoints are disabled");
        }
    }

    private void validateFiles(AgentOpenApiAuthService.AuthenticatedCredential auth,
                               List<Map<String, Object>> files,
                               String externalUserId,
                               String conversationId) {
        if (files == null || files.isEmpty()) {
            return;
        }
        authService.requireScope(auth, "files");
        List<String> ids = files.stream()
                .map(item -> text(firstRaw(item, "id", "file_id", "upload_file_id")))
                .filter(value -> !value.isBlank())
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<String, AgentApiFileEntity> found = new LinkedHashMap<>();
        fileRepository.findByOrgIdAndCredentialIdAndAgentIdAndFileIdIn(
                        auth.credential().getOrgId(),
                        auth.credential().getId(),
                        auth.credential().getAgentId(),
                        ids)
                .forEach(item -> found.put(item.getFileId(), item));
        for (String id : ids) {
            AgentApiFileEntity file = found.get(id);
            if (file == null
                    || (!text(file.getExternalUserId()).isBlank() && !text(file.getExternalUserId()).equals(text(externalUserId)))
                    || (!text(file.getExternalSessionId()).isBlank() && !text(file.getExternalSessionId()).equals(text(conversationId)))) {
                throw new AgentOpenApiException(HttpStatus.FORBIDDEN, "file_not_allowed", "files must belong to this API key, Agent and user");
            }
        }
    }

    private Object firstRaw(Map<String, Object> map, String... keys) {
        if (map == null) {
            return "";
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    @Transactional
    protected AgentApiMessageEntity saveMessage(String messageId,
                                                String requestId,
                                                String taskId,
                                                AgentOpenApiRunService.ChatExecution execution,
                                                ChatMessageInput input,
                                                String status,
                                                String errorCode,
                                                String idempotencyKey) {
        return messageRepository.save(new AgentApiMessageEntity(
                messageId,
                requestId,
                taskId,
                execution.auth().credential().getOrgId(),
                execution.auth().credential().getId(),
                execution.auth().credential().getAgentId(),
                input.externalUserId(),
                execution.session().externalSessionId(),
                execution.session().internalSessionId(),
                input.query(),
                execution.answer(),
                status,
                errorCode,
                idempotencyKey.isBlank() ? null : idempotencyKey,
                toJson(Map.of(
                        "activeSkillCode", input.activeSkillCode(),
                        "knowledgeBaseIds", input.knowledgeBaseIds(),
                        "metadata", input.metadata()))));
    }

    private AgentApiSessionMapEntity requireConversation(AgentOpenApiAuthService.AuthenticatedCredential auth, String conversationId) {
        return sessionMapRepository
                .findByOrgIdAndCredentialIdAndAgentIdAndExternalSessionIdAndDeletedAtIsNull(
                        auth.credential().getOrgId(),
                        auth.credential().getId(),
                        auth.credential().getAgentId(),
                        text(conversationId))
                .orElseThrow(() -> new AgentOpenApiException(HttpStatus.NOT_FOUND, "conversation_not_found", "Conversation not found"));
    }

    private AgentApiMessageEntity requireMessage(AgentOpenApiAuthService.AuthenticatedCredential auth, String messageId) {
        return messageRepository
                .findByMessageIdAndOrgIdAndCredentialIdAndAgentId(
                        text(messageId),
                        auth.credential().getOrgId(),
                        auth.credential().getId(),
                        auth.credential().getAgentId())
                .orElseThrow(() -> new AgentOpenApiException(HttpStatus.NOT_FOUND, "message_not_found", "Message not found"));
    }

    private Map<String, Object> messagePayload(AgentApiMessageEntity message, Map<String, Object> metadata) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "message");
        payload.put("task_id", message.getTaskId());
        payload.put("message_id", message.getMessageId());
        payload.put("conversation_id", message.getExternalSessionId());
        payload.put("query", text(message.getQuery()));
        payload.put("answer", text(message.getAnswer()));
        payload.put("created_at", message.getCreatedAt().toString());
        Map<String, Object> feedback = latestFeedback(message.getMessageId());
        payload.put("feedback", feedback);
        payload.put("metadata", enrichMetadata(message, metadata, feedback));
        return payload;
    }

    private Map<String, Object> enrichMetadata(AgentApiMessageEntity message, Map<String, Object> extra, Map<String, Object> feedback) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("usage", Map.of());
        metadata.put("retriever_resources", List.of());
        metadata.put("agent_thoughts", List.of());
        metadata.putAll(readMap(message.getMetadataJson()));
        metadata.put("feedback", feedback);
        metadata.putAll(extra);
        return metadata;
    }

    private Map<String, Object> latestFeedback(String messageId) {
        return feedbackRepository.findByMessageIdOrderByCreatedAtDesc(messageId)
                .stream()
                .findFirst()
                .map(item -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("rating", item.getRating());
                    payload.put("content", text(item.getContent()));
                    payload.put("created_at", item.getCreatedAt().toString());
                    return payload;
                })
                .orElseGet(Map::of);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private int pageStart(List<Map<String, Object>> candidates, String firstId) {
        String cursor = text(firstId);
        if (cursor.isBlank()) {
            return 0;
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (cursor.equals(text(candidates.get(i).get("message_id")))) {
                return i + 1;
            }
        }
        return 0;
    }

    private Map<String, Object> usage(int elapsedMs) {
        return Map.of("elapsed_ms", elapsedMs);
    }

    private Map<String, Object> conversationPayload(AgentApiSessionMapEntity item) {
        return Map.of(
                "id", item.getExternalSessionId(),
                "name", text(item.getConversationName()).isBlank() ? item.getExternalSessionId() : item.getConversationName(),
                "external_user_id", text(item.getExternalUserId()),
                "created_at", item.getCreatedAt().toString(),
                "updated_at", item.getUpdatedAt().toString());
    }

    private Map<String, Object> filePayload(AgentApiFileEntity file) {
        return Map.of(
                "id", file.getFileId(),
                "name", file.getName(),
                "size", file.getSizeBytes(),
                "mime_type", text(file.getMimeType()),
                "created_at", file.getCreatedAt().toString());
    }

    private void sendError(SseEmitter emitter, String requestId, AgentOpenApiException ex) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of(
                    "event", "error",
                    "requestId", requestId,
                    "code", ex.getCode(),
                    "message", ex.getMessage())));
            emitter.complete();
        } catch (IOException ignored) {
            emitter.completeWithError(ex);
        }
    }

    private String normalizeRating(String rating) {
        String value = text(rating).toLowerCase(Locale.ROOT);
        if (value.equals("like") || value.equals("dislike")) {
            return value;
        }
        throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "invalid_request", "rating must be like or dislike");
    }

    private String normalizeIdempotency(String value) {
        return clip(value, 128);
    }

    private String firstText(String first, String second) {
        String value = text(first);
        return value.isBlank() ? text(second) : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String clip(String value, int max) {
        String text = text(value);
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max));
    }

    private String id(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_REF);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String normalizeMimeType(String contentType, String filename) {
        String value = text(contentType).toLowerCase(Locale.ROOT);
        if (!value.isBlank() && !"application/octet-stream".equals(value)) {
            return value;
        }
        String name = text(filename).toLowerCase(Locale.ROOT);
        if (name.endsWith(".txt")) return "text/plain";
        if (name.endsWith(".md")) return "text/markdown";
        if (name.endsWith(".csv")) return "text/csv";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".doc")) return "application/msword";
        if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        return value;
    }

    private boolean isAllowedUploadMimeType(String mimeType) {
        String value = text(mimeType).toLowerCase(Locale.ROOT);
        return value.startsWith("image/") || ALLOWED_DOCUMENT_MIME_TYPES.contains(value);
    }

    public record ChatMessageCommand(
            String query,
            String message,
            String user,
            @JsonAlias("response_mode")
            String responseMode,
            @JsonAlias("conversation_id")
            String conversationId,
            @JsonAlias("session_id")
            String sessionId,
            @JsonAlias("external_user")
            Map<String, Object> externalUser,
            Map<String, Object> inputs,
            List<Map<String, Object>> files,
            @JsonAlias("auto_generate_name")
            Boolean autoGenerateName,
            @JsonAlias("knowledge_base_ids")
            List<String> knowledgeBaseIds,
            @JsonAlias("active_skill_code")
            String activeSkillCode,
            Map<String, Object> metadata,
            @JsonAlias("cloudcc_context")
            AgentOpenApiRunService.CloudccContext cloudccContext
    ) {
    }

    private record ChatMessageInput(
            String query,
            String conversationId,
            String externalUserId,
            Map<String, Object> externalUser,
            List<String> knowledgeBaseIds,
            String activeSkillCode,
            Map<String, Object> metadata,
            AgentOpenApiRunService.CloudccContext cloudccContext,
            List<Map<String, Object>> files
    ) {
    }

    public record RenameConversationCommand(String name) {
    }

    public record FeedbackCommand(String rating, String content) {
    }

    public record MessagePage(List<Map<String, Object>> data, boolean hasMore, int limit) {
    }
}
