package com.codehouse.ciciassistant.openapi.service;

import com.codehouse.ciciassistant.ai.domain.AgentRunTraceEntity;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceRepository;
import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AgentOpenApiRunService {

    private static final TypeReference<List<Object>> LIST_OBJECT_REF = new TypeReference<>() {};

    private final AgentOpenApiAuthService authService;
    private final AgentOpenApiSessionService sessionService;
    private final AgentOpenApiRateLimitService rateLimitService;
    private final AgentOpenApiCallLogService callLogService;
    private final ChatOrchestratorService chatOrchestratorService;
    private final AgentRunTraceRepository traceRepository;
    private final ObjectMapper objectMapper;

    public AgentOpenApiRunService(AgentOpenApiAuthService authService,
                                  AgentOpenApiSessionService sessionService,
                                  AgentOpenApiRateLimitService rateLimitService,
                                  AgentOpenApiCallLogService callLogService,
                                  ChatOrchestratorService chatOrchestratorService,
                                  AgentRunTraceRepository traceRepository,
                                  ObjectMapper objectMapper) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.rateLimitService = rateLimitService;
        this.callLogService = callLogService;
        this.chatOrchestratorService = chatOrchestratorService;
        this.traceRepository = traceRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> chat(String agentId,
                                    String requestId,
                                    String idempotencyKey,
                                    ChatCommand command,
                                    HttpServletRequest request) {
        Instant startedAt = Instant.now();
        AgentOpenApiAuthService.AuthenticatedCredential auth = authService.authenticate(agentId, request);
        String externalUserId = command == null ? "" : externalUserId(command.externalUser());
        validateCommand(command, auth.credentialView());
        AgentOpenApiSessionService.SessionResolution session = sessionService.resolve(
                auth,
                command.sessionId(),
                externalUserId,
                requestId);
        rateLimitService.reserve(auth);
        callLogService.start(auth, session, requestId, externalUserId, idempotencyKey, command.message());
        try {
            Map<String, Object> chatPayload = chatOrchestratorService.chat(
                    auth.credential().getOrgId(),
                    auth.credential().getRunAsUserId(),
                    session.internalSessionId(),
                    command.message().trim(),
                    command.knowledgeBaseIds(),
                    auth.credential().getAgentId(),
                    command.activeSkillCode());
            String answer = stringValue(chatPayload.get("answer"));
            AgentRunTraceEntity trace = annotateLatestTrace(auth, session, requestId, externalUserId);
            int elapsedMs = elapsedMs(startedAt, Instant.now());
            callLogService.completeSuccess(auth.credential().getId(), requestId, trace == null ? "" : trace.getTraceId(), answer, elapsedMs);
            rateLimitService.markSuccess(auth, elapsedMs);
            return responsePayload(auth, session, requestId, chatPayload, trace, answer, elapsedMs);
        } catch (AgentOpenApiException ex) {
            int elapsedMs = elapsedMs(startedAt, Instant.now());
            callLogService.completeFailure(
                    auth.credential().getId(),
                    requestId,
                    ex.getStatus().value(),
                    ex.getCode(),
                    elapsedMs,
                    ex.getMessage());
            rateLimitService.markFailure(auth, elapsedMs);
            throw ex;
        } catch (RuntimeException ex) {
            int elapsedMs = elapsedMs(startedAt, Instant.now());
            callLogService.completeFailure(
                    auth.credential().getId(),
                    requestId,
                    HttpStatus.BAD_GATEWAY.value(),
                    "model_or_tool_failed",
                    elapsedMs,
                    ex.getMessage());
            rateLimitService.markFailure(auth, elapsedMs);
            throw new AgentOpenApiException(
                    HttpStatus.BAD_GATEWAY,
                    "model_or_tool_failed",
                    "Agent runtime failed");
        }
    }

    public SseEmitter chatStream(String agentId,
                                 String requestId,
                                 String idempotencyKey,
                                 ChatCommand command,
                                 HttpServletRequest request) {
        Instant startedAt = Instant.now();
        AgentOpenApiAuthService.AuthenticatedCredential auth = authService.authenticate(agentId, request);
        String externalUserId = command == null ? "" : externalUserId(command.externalUser());
        validateCommand(command, auth.credentialView());
        if (!auth.credentialView().allowStream()) {
            throw new AgentOpenApiException(HttpStatus.FORBIDDEN, "stream_not_allowed", "Streaming is not allowed for this API key");
        }
        AgentOpenApiSessionService.SessionResolution session = sessionService.resolve(
                auth,
                command.sessionId(),
                externalUserId,
                requestId);
        rateLimitService.reserve(auth);
        callLogService.start(auth, session, requestId, externalUserId, idempotencyKey, command.message());

        SseEmitter clientEmitter = new SseEmitter(600_000L);
        OpenApiStreamBridge bridge = new OpenApiStreamBridge(
                clientEmitter,
                auth,
                session,
                requestId,
                externalUserId,
                startedAt);
        try {
            clientEmitter.send(SseEmitter.event()
                    .name("meta")
                    .data(Map.of(
                            "requestId", requestId,
                            "agentId", auth.credential().getAgentId(),
                            "sessionId", session.externalSessionId(),
                            "internalSessionId", session.internalSessionId()
                    )));
            chatOrchestratorService.chatStream(
                    auth.credential().getOrgId(),
                    auth.credential().getRunAsUserId(),
                    session.internalSessionId(),
                    command.message().trim(),
                    command.knowledgeBaseIds(),
                    auth.credential().getAgentId(),
                    command.activeSkillCode(),
                    bridge);
        } catch (IOException ex) {
            bridge.completeWithError(ex);
        } catch (RuntimeException ex) {
            bridge.completeWithError(ex);
        }
        return clientEmitter;
    }

    @Transactional
    protected AgentRunTraceEntity annotateLatestTrace(AgentOpenApiAuthService.AuthenticatedCredential auth,
                                                      AgentOpenApiSessionService.SessionResolution session,
                                                      String requestId,
                                                      String externalUserId) {
        return traceRepository
                .findFirstByOrgIdAndSessionIdAndAgentIdOrderByStartedAtDesc(
                        auth.credential().getOrgId(),
                        session.internalSessionId(),
                        auth.credential().getAgentId())
                .map(trace -> {
                    trace.markOpenApi(requestId, auth.credential().getId(), externalUserId);
                    return traceRepository.save(trace);
                })
                .orElse(null);
    }

    private Map<String, Object> responsePayload(AgentOpenApiAuthService.AuthenticatedCredential auth,
                                                AgentOpenApiSessionService.SessionResolution session,
                                                String requestId,
                                                Map<String, Object> chatPayload,
                                                AgentRunTraceEntity trace,
                                                String answer,
                                                int elapsedMs) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", requestId);
        data.put("agentId", auth.credential().getAgentId());
        data.put("sessionId", session.externalSessionId());
        data.put("internalSessionId", session.internalSessionId());
        data.put("traceId", trace == null ? "" : trace.getTraceId());
        data.put("answer", answer);
        data.put("status", "completed");
        data.put("model", mapValue(chatPayload.get("model")));
        data.put("runtime", runtimePayload(chatPayload, trace));
        data.put("elapsedMs", elapsedMs);
        return data;
    }

    private Map<String, Object> runtimePayload(Map<String, Object> chatPayload, AgentRunTraceEntity trace) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("activatedSkillCodes", trace == null ? List.of() : readList(trace.getSkillNamesJson()));
        runtime.put("boundSkillCodes", listValue(chatPayload.get("resolvedSkills")));
        runtime.put("toolCallCount", trace == null ? 0 : trace.getToolCallCount());
        runtime.put("ragContextCount", trace == null ? listValue(chatPayload.get("ragContext")).size() : trace.getRagContextCount());
        return runtime;
    }

    private void validateCommand(ChatCommand command, AgentOpenApiCredentialService.CredentialView credential) {
        if (command == null) {
            throw invalid("Request body is required");
        }
        String message = command.message() == null ? "" : command.message().trim();
        if (message.isBlank()) {
            throw invalid("message is required");
        }
        if (message.length() > credential.maxPromptChars()) {
            throw invalid("message exceeds maxPromptChars");
        }
        String externalUserId = externalUserId(command.externalUser());
        if (externalUserId.length() > 128) {
            throw invalid("externalUser.id must be 128 characters or fewer");
        }
        if (jsonSize(command.externalUser()) > 4096) {
            throw invalid("externalUser metadata is too large");
        }
        if (jsonSize(command.metadata()) > 4096) {
            throw invalid("metadata is too large");
        }
    }

    private AgentOpenApiException invalid(String message) {
        return new AgentOpenApiException(HttpStatus.BAD_REQUEST, "invalid_request", message);
    }

    private String externalUserId(Map<String, Object> externalUser) {
        if (externalUser == null || externalUser.isEmpty()) {
            return "";
        }
        Object raw = externalUser.get("id");
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private int jsonSize(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return objectMapper.writeValueAsString(value).length();
        } catch (JsonProcessingException ex) {
            throw invalid("metadata must be valid JSON");
        }
    }

    private List<Object> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, LIST_OBJECT_REF);
        } catch (Exception ex) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Object> listValue(Object value) {
        if (value instanceof List<?> raw) {
            return (List<Object>) raw;
        }
        return List.of();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> streamRuntimePayload(AgentRunTraceEntity trace) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("activatedSkillCodes", trace == null ? List.of() : readList(trace.getSkillNamesJson()));
        runtime.put("boundSkillCodes", List.of());
        runtime.put("toolCallCount", trace == null ? 0 : trace.getToolCallCount());
        runtime.put("ragContextCount", trace == null ? 0 : trace.getRagContextCount());
        return runtime;
    }

    private static int elapsedMs(Instant start, Instant end) {
        return (int) Math.max(0L, Duration.between(start, end).toMillis());
    }

    private final class OpenApiStreamBridge extends SseEmitter {

        private final SseEmitter clientEmitter;
        private final AgentOpenApiAuthService.AuthenticatedCredential auth;
        private final AgentOpenApiSessionService.SessionResolution session;
        private final String requestId;
        private final String externalUserId;
        private final Instant startedAt;
        private final StringBuilder streamedAnswer = new StringBuilder();
        private final AtomicBoolean finished = new AtomicBoolean(false);

        private OpenApiStreamBridge(SseEmitter clientEmitter,
                                    AgentOpenApiAuthService.AuthenticatedCredential auth,
                                    AgentOpenApiSessionService.SessionResolution session,
                                    String requestId,
                                    String externalUserId,
                                    Instant startedAt) {
            super(600_000L);
            this.clientEmitter = clientEmitter;
            this.auth = auth;
            this.session = session;
            this.requestId = requestId;
            this.externalUserId = externalUserId;
            this.startedAt = startedAt;
        }

        @Override
        public void send(Object object) throws IOException {
            clientEmitter.send(object);
        }

        @Override
        public void send(Object object, MediaType mediaType) throws IOException {
            clientEmitter.send(object, mediaType);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            Set<ResponseBodyEmitter.DataWithMediaType> dataToSend = builder.build();
            String eventName = eventName(dataToSend);
            if ("delta".equals(eventName)) {
                appendDelta(dataToSend);
            }
            if ("done".equals(eventName)) {
                return;
            }
            clientEmitter.send(dataToSend);
        }

        @Override
        public void complete() {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            try {
                AgentRunTraceEntity trace = annotateLatestTrace(auth, session, requestId, externalUserId);
                int elapsedMs = elapsedMs(startedAt, Instant.now());
                String answer = streamedAnswer.toString();
                callLogService.completeSuccess(
                        auth.credential().getId(),
                        requestId,
                        trace == null ? "" : trace.getTraceId(),
                        answer,
                        elapsedMs);
                rateLimitService.markSuccess(auth, elapsedMs);
                clientEmitter.send(SseEmitter.event()
                        .name("done")
                        .data(Map.of(
                                "ok", true,
                                "requestId", requestId,
                                "traceId", trace == null ? "" : trace.getTraceId(),
                                "elapsedMs", elapsedMs,
                                "runtime", streamRuntimePayload(trace)
                        )));
                clientEmitter.complete();
            } catch (IOException ex) {
                clientEmitter.completeWithError(ex);
            }
        }

        @Override
        public void completeWithError(Throwable ex) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            int elapsedMs = elapsedMs(startedAt, Instant.now());
            callLogService.completeFailure(
                    auth.credential().getId(),
                    requestId,
                    HttpStatus.BAD_GATEWAY.value(),
                    "model_or_tool_failed",
                    elapsedMs,
                    ex == null ? "stream failed" : ex.getMessage());
            rateLimitService.markFailure(auth, elapsedMs);
            clientEmitter.completeWithError(ex);
        }

        private void appendDelta(Set<ResponseBodyEmitter.DataWithMediaType> dataToSend) {
            for (ResponseBodyEmitter.DataWithMediaType item : dataToSend) {
                Object data = item.getData();
                if (data instanceof Map<?, ?> payload) {
                    Object text = payload.get("text");
                    if (text != null) {
                        streamedAnswer.append(text);
                    }
                }
            }
        }

        private String eventName(Set<ResponseBodyEmitter.DataWithMediaType> dataToSend) {
            for (ResponseBodyEmitter.DataWithMediaType item : dataToSend) {
                Object data = item.getData();
                if (data instanceof String text && text.startsWith("event:")) {
                    int end = text.indexOf('\n');
                    return (end < 0 ? text.substring("event:".length()) : text.substring("event:".length(), end)).trim();
                }
            }
            return "";
        }
    }

    public record ChatCommand(
            String sessionId,
            String message,
            Map<String, Object> externalUser,
            List<String> knowledgeBaseIds,
            String activeSkillCode,
            Map<String, Object> metadata
    ) {
    }
}
