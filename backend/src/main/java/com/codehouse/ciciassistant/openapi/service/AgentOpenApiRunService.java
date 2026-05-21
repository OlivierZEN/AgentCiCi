package com.codehouse.ciciassistant.openapi.service;

import com.codehouse.ciciassistant.ai.domain.AgentRunTraceEntity;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceRepository;
import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentKnowledgeBindingRepository;
import com.codehouse.ciciassistant.model.domain.OrgModelConfigEntity;
import com.codehouse.ciciassistant.model.domain.OrgModelConfigRepository;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.codehouse.ciciassistant.openapi.config.AgentOpenApiProperties;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialEntity;
import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentOpenApiRunService {

    private static final TypeReference<List<Object>> LIST_OBJECT_REF = new TypeReference<>() {};

    private final AgentOpenApiAuthService authService;
    private final AgentOpenApiSessionService sessionService;
    private final AgentOpenApiRateLimitService rateLimitService;
    private final AgentOpenApiCallLogService callLogService;
    private final ChatOrchestratorService chatOrchestratorService;
    private final AgentRunTraceRepository traceRepository;
    private final AgentKnowledgeBindingRepository knowledgeBindingRepository;
    private final SkillDefinitionRepository skillDefinitionRepository;
    private final AgentSkillBindingRepository agentSkillBindingRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final OrgModelConfigRepository orgModelConfigRepository;
    private final ModelProviderService modelProviderService;
    private final CloudccAccessTokenService cloudccAccessTokenService;
    private final AgentOpenApiProperties properties;
    private final ObjectMapper objectMapper;

    public AgentOpenApiRunService(AgentOpenApiAuthService authService,
                                  AgentOpenApiSessionService sessionService,
                                  AgentOpenApiRateLimitService rateLimitService,
                                  AgentOpenApiCallLogService callLogService,
                                  ChatOrchestratorService chatOrchestratorService,
                                  AgentRunTraceRepository traceRepository,
                                  AgentKnowledgeBindingRepository knowledgeBindingRepository,
                                  SkillDefinitionRepository skillDefinitionRepository,
                                  AgentSkillBindingRepository agentSkillBindingRepository,
                                  AgentDefinitionRepository agentDefinitionRepository,
                                  OrgModelConfigRepository orgModelConfigRepository,
                                  ModelProviderService modelProviderService,
                                  CloudccAccessTokenService cloudccAccessTokenService,
                                  AgentOpenApiProperties properties,
                                  ObjectMapper objectMapper) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.rateLimitService = rateLimitService;
        this.callLogService = callLogService;
        this.chatOrchestratorService = chatOrchestratorService;
        this.traceRepository = traceRepository;
        this.knowledgeBindingRepository = knowledgeBindingRepository;
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.agentSkillBindingRepository = agentSkillBindingRepository;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.orgModelConfigRepository = orgModelConfigRepository;
        this.modelProviderService = modelProviderService;
        this.cloudccAccessTokenService = cloudccAccessTokenService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ChatExecution chatWithAuth(AgentOpenApiAuthService.AuthenticatedCredential auth,
                                      String requestId,
                                      String idempotencyKey,
                                      ChatCommand command,
                                      HttpServletRequest request,
                                      Instant startedAt) {
        String externalUserId = command == null ? "" : externalUserId(command.externalUser());
        validateCommand(command, auth);
        CloudccAccessTokenService.CloudccSessionContext cloudccOverride = resolveCloudccContext(auth, command);
        ensureChatRouteHasUsableModel(auth);
        AgentOpenApiSessionService.SessionResolution session = sessionService.resolve(
                auth,
                command.sessionId(),
                externalUserId,
                requestId);
        rateLimitService.reserve(auth);
        callLogService.start(auth, session, requestId, externalUserId, idempotencyKey, command.message());
        try {
            Map<String, Object> chatPayload = invokeChatWithTimeout(auth, session, command, cloudccOverride);
            String answer = stringValue(chatPayload.get("answer"));
            validateResponseSize(auth, answer);
            AgentRunTraceEntity trace = annotateLatestTrace(auth, session, requestId, externalUserId);
            int elapsedMs = elapsedMs(startedAt, Instant.now());
            callLogService.completeSuccess(auth.credential().getId(), requestId, trace == null ? "" : trace.getTraceId(), answer, elapsedMs);
            rateLimitService.markSuccess(auth, elapsedMs);
            Map<String, Object> payload = responsePayload(auth, session, requestId, chatPayload, trace, answer, elapsedMs);
            return new ChatExecution(auth, session, requestId, trace == null ? "" : trace.getTraceId(), answer, elapsedMs, payload);
        } catch (AgentOpenApiException ex) {
            int elapsedMs = elapsedMs(startedAt, Instant.now());
            callLogService.completeFailure(
                    auth.credential().getId(),
                    requestId,
                    ex.getStatus().value(),
                    ex.getCode(),
                    elapsedMs,
                    sanitizeFailureMessage(ex.getMessage(), cloudccOverride));
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
                    sanitizeFailureMessage(ex.getMessage(), cloudccOverride));
            rateLimitService.markFailure(auth, elapsedMs);
            throw new AgentOpenApiException(
                    HttpStatus.BAD_GATEWAY,
                    "model_or_tool_failed",
                    "Agent runtime failed");
        }
    }

    private Map<String, Object> invokeChatWithTimeout(AgentOpenApiAuthService.AuthenticatedCredential auth,
                                                      AgentOpenApiSessionService.SessionResolution session,
                                                      ChatCommand command,
                                                      CloudccAccessTokenService.CloudccSessionContext cloudccOverride) {
        CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
            if (cloudccOverride == null) {
                return invokeChat(auth, session, command);
            }
            return cloudccAccessTokenService.withSessionContextOverride(
                    auth.credential().getOrgId(),
                    auth.credential().getRunAsUserId(),
                    cloudccOverride,
                    () -> invokeChat(auth, session, command));
        });
        try {
            return future.get(normalizedTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new AgentOpenApiException(HttpStatus.GATEWAY_TIMEOUT, "agent_open_api_timeout", "Agent runtime timed out");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AgentOpenApiException(HttpStatus.GATEWAY_TIMEOUT, "agent_open_api_timeout", "Agent runtime interrupted");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof AgentOpenApiException apiException) {
                throw apiException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AgentOpenApiException(HttpStatus.BAD_GATEWAY, "model_or_tool_failed", "Agent runtime failed");
        }
    }

    private Map<String, Object> invokeChat(AgentOpenApiAuthService.AuthenticatedCredential auth,
                                           AgentOpenApiSessionService.SessionResolution session,
                                           ChatCommand command) {
        return chatOrchestratorService.chat(
                auth.credential().getOrgId(),
                auth.credential().getRunAsUserId(),
                session.internalSessionId(),
                command.message().trim(),
                command.knowledgeBaseIds(),
                auth.credential().getAgentId(),
                command.activeSkillCode());
    }

    private long normalizedTimeoutMs() {
        long configured = properties.getDefaultTimeoutMs();
        return configured <= 0 ? 120_000L : Math.min(configured, 600_000L);
    }

    private void validateResponseSize(AgentOpenApiAuthService.AuthenticatedCredential auth, String answer) {
        int maxResponseChars = Math.max(1, auth.credentialView().maxResponseChars());
        if (answer != null && answer.length() > maxResponseChars) {
            throw new AgentOpenApiException(HttpStatus.BAD_GATEWAY, "response_too_large", "answer exceeds maxResponseChars");
        }
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

    private void validateCommand(ChatCommand command, AgentOpenApiAuthService.AuthenticatedCredential auth) {
        if (command == null) {
            throw invalid("Request body is required");
        }
        AgentOpenApiCredentialService.CredentialView credential = auth.credentialView();
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
        validateKnowledgeBaseScope(auth, command.knowledgeBaseIds());
        validateSkillScope(auth, command.activeSkillCode());
    }

    private CloudccAccessTokenService.CloudccSessionContext resolveCloudccContext(
            AgentOpenApiAuthService.AuthenticatedCredential auth,
            ChatCommand command) {
        String keyType = auth.credentialView().keyType() == null
                ? AgentApiCredentialEntity.KEY_TYPE_STANDARD
                : auth.credentialView().keyType().trim().toLowerCase(java.util.Locale.ROOT);
        CloudccContext context = command.cloudccContext();
        if (AgentApiCredentialEntity.KEY_TYPE_STANDARD.equals(keyType)) {
            if (hasCloudccContext(context)) {
                throw new AgentOpenApiException(
                        HttpStatus.BAD_REQUEST,
                        "cloudcc_context_not_allowed",
                        "cloudccContext is only allowed for cloudcc API keys");
            }
            return null;
        }
        if (!AgentApiCredentialEntity.KEY_TYPE_CLOUDCC.equals(keyType)) {
            throw new AgentOpenApiException(HttpStatus.FORBIDDEN, "unsupported_key_type", "API key type is unsupported");
        }
        String accessToken = context == null ? null : trimToNull(context.accessToken());
        if (accessToken == null) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "cloudcc_token_required", "cloudccContext.accessToken is required");
        }
        validateCloudccToken(accessToken);
        String baseUrl = resolveCloudccBaseUrl(auth.credential().getOrgId(), context);
        String setupSvc = resolveCloudccSetupSvc(baseUrl, context);
        return new CloudccAccessTokenService.CloudccSessionContext(accessToken, baseUrl, setupSvc);
    }

    private boolean hasCloudccContext(CloudccContext context) {
        return context != null
                && (trimToNull(context.accessToken()) != null
                || trimToNull(context.baseUrl()) != null
                || trimToNull(context.setupSvc()) != null);
    }

    private void validateCloudccToken(String accessToken) {
        if (accessToken.length() > 8192 || accessToken.chars().anyMatch(Character::isWhitespace)) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "cloudcc_context_invalid", "cloudccContext is invalid");
        }
        Optional<Instant> expiresAt = parseJwtExp(accessToken);
        if (expiresAt.isPresent() && !expiresAt.get().isAfter(Instant.now().plusSeconds(30))) {
            throw new AgentOpenApiException(HttpStatus.UNAUTHORIZED, "cloudcc_token_rejected", "CloudCC token is expired or rejected");
        }
    }

    private String resolveCloudccBaseUrl(String orgId, CloudccContext context) {
        String configured = cloudccAccessTokenService.getConfiguredGateway(orgId)
                .map(CloudccAccessTokenService.CloudccGatewayContext::baseUrl)
                .orElse("");
        String rawBaseUrl = trimToNull(context == null ? null : context.baseUrl());
        if (rawBaseUrl == null) {
            if (configured.isBlank()) {
                throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "cloudcc_context_invalid", "cloudccContext.baseUrl is required");
            }
            return configured;
        }
        return normalizeAllowedCloudccUrl(rawBaseUrl, configured, List.of("/lightningapi", "/ccdomaingateway/apisvc"));
    }

    private String resolveCloudccSetupSvc(String baseUrl, CloudccContext context) {
        String rawSetupSvc = trimToNull(context == null ? null : context.setupSvc());
        if (rawSetupSvc == null) {
            return CloudccAccessTokenService.deriveSetupSvc(baseUrl);
        }
        return normalizeAllowedCloudccUrl(rawSetupSvc, baseUrl, List.of("/setup", "/ccdomaingateway/setup"));
    }

    private String normalizeAllowedCloudccUrl(String raw, String configuredPeer, List<String> allowedPathPrefixes) {
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (Exception ex) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "cloudcc_base_url_denied", "CloudCC base URL is not allowed");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if ((!scheme.equals("https") && !scheme.equals("http"))
                || host.isBlank()
                || uri.getUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || isDeniedCloudccHost(host)
                || !isAllowedCloudccHost(host, configuredPeer)) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "cloudcc_base_url_denied", "CloudCC base URL is not allowed");
        }
        String path = uri.getPath() == null || uri.getPath().isBlank() ? allowedPathPrefixes.iterator().next() : trimTrailingSlash(uri.getPath());
        String normalizedPath = path.toLowerCase(java.util.Locale.ROOT);
        if (!isAllowedCloudccPath(normalizedPath, allowedPathPrefixes)) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "cloudcc_base_url_denied", "CloudCC base URL is not allowed");
        }
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), path, null, null).toString();
        } catch (Exception ex) {
            throw new AgentOpenApiException(HttpStatus.BAD_REQUEST, "cloudcc_base_url_denied", "CloudCC base URL is not allowed");
        }
    }

    private boolean isAllowedCloudccPath(String normalizedPath, List<String> allowedPathPrefixes) {
        for (String prefix : allowedPathPrefixes) {
            if (normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedCloudccHost(String host, String configuredPeer) {
        String configuredHost = "";
        if (configuredPeer != null && !configuredPeer.isBlank()) {
            try {
                configuredHost = URI.create(configuredPeer).getHost();
                configuredHost = configuredHost == null ? "" : configuredHost.toLowerCase(java.util.Locale.ROOT);
            } catch (Exception ignored) {
                configuredHost = "";
            }
        }
        return host.endsWith(".apis.cloudcc.cn")
                || host.endsWith(".lightning.cloudcc.cn")
                || (!configuredHost.isBlank() && host.equals(configuredHost));
    }

    private boolean isDeniedCloudccHost(String host) {
        return host.equals("localhost")
                || host.equals("127.0.0.1")
                || host.equals("0.0.0.0")
                || host.equals("::1")
                || host.endsWith(".local")
                || host.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$")
                || host.contains(":");
    }

    private Optional<Instant> parseJwtExp(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return Optional.empty();
            }
            byte[] bytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(bytes, new TypeReference<>() {});
            Object exp = payload.get("exp");
            if (exp instanceof Number number && number.longValue() > 0L) {
                return Optional.of(Instant.ofEpochSecond(number.longValue()));
            }
            return Optional.empty();
        } catch (Exception ex) {
            throw new AgentOpenApiException(HttpStatus.UNAUTHORIZED, "cloudcc_token_rejected", "CloudCC token is expired or rejected");
        }
    }

    private String sanitizeFailureMessage(String message, CloudccAccessTokenService.CloudccSessionContext cloudccOverride) {
        String value = message == null ? "" : message;
        if (cloudccOverride != null && cloudccOverride.accessToken() != null && !cloudccOverride.accessToken().isBlank()) {
            value = value.replace(cloudccOverride.accessToken(), "[redacted]");
        }
        return value;
    }

    private AgentOpenApiException invalid(String message) {
        return new AgentOpenApiException(HttpStatus.BAD_REQUEST, "invalid_request", message);
    }

    private void ensureChatRouteHasUsableModel(AgentOpenApiAuthService.AuthenticatedCredential auth) {
        String orgId = auth.credential().getOrgId();
        String agentId = auth.credential().getAgentId();
        OrgModelConfigEntity current = orgModelConfigRepository.findByOrgIdAndSceneCode(orgId, "chat").orElse(null);
        if (current != null && !isPlaceholderModelRoute(current)) {
            return;
        }

        ModelChoice choice = resolveOpenApiModelChoice(orgId, agentId);
        if (choice == null) {
            return;
        }

        OrgModelConfigEntity target = current == null
                ? new OrgModelConfigEntity(orgId, "chat", choice.providerCode(), choice.modelName())
                : current;
        target.update(choice.providerCode(), choice.modelName());
        orgModelConfigRepository.save(target);
    }

    private ModelChoice resolveOpenApiModelChoice(String orgId, String agentId) {
        String agentModel = agentDefinitionRepository.findByOrgIdAndAgentId(orgId, agentId)
                .map(agent -> trimToNull(agent.getModel()))
                .orElse(null);
        List<ModelChoice> choices = modelProviderService.agentBaseModels(orgId).stream()
                .map(this::toModelChoice)
                .filter(choice -> choice != null)
                .toList();
        if (choices.isEmpty()) {
            return null;
        }
        if (agentModel != null) {
            for (ModelChoice choice : choices) {
                if (agentModel.equalsIgnoreCase(choice.modelName())) {
                    return choice;
                }
            }
        }
        return choices.get(0);
    }

    private ModelChoice toModelChoice(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        String providerCode = trimToNull(stringValue(row.get("providerCode")));
        String modelName = trimToNull(stringValue(row.get("modelName")));
        return providerCode == null || modelName == null ? null : new ModelChoice(providerCode, modelName);
    }

    private boolean isPlaceholderModelRoute(OrgModelConfigEntity route) {
        return "mock".equalsIgnoreCase(nullToEmpty(route.getProvider()).trim())
                || "cici-default".equalsIgnoreCase(nullToEmpty(route.getModelName()).trim());
    }

    private String externalUserId(Map<String, Object> externalUser) {
        if (externalUser == null || externalUser.isEmpty()) {
            return "";
        }
        Object raw = externalUser.get("id");
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private void validateKnowledgeBaseScope(AgentOpenApiAuthService.AuthenticatedCredential auth, List<String> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return;
        }
        Set<String> allowedIds = knowledgeBindingRepository
                .findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(
                        auth.credential().getOrgId(),
                        auth.credential().getAgentId())
                .stream()
                .map(item -> String.valueOf(item.getKnowledgeBaseId()))
                .collect(java.util.stream.Collectors.toSet());
        for (String raw : knowledgeBaseIds) {
            String value = raw == null ? "" : raw.trim();
            if (value.isBlank() || !allowedIds.contains(value)) {
                throw new AgentOpenApiException(HttpStatus.FORBIDDEN, "knowledge_base_not_allowed", "knowledgeBaseIds must be bound to this Agent");
            }
        }
    }

    private void validateSkillScope(AgentOpenApiAuthService.AuthenticatedCredential auth, String activeSkillCode) {
        String code = activeSkillCode == null ? "" : activeSkillCode.trim();
        if (code.isBlank()) {
            return;
        }
        SkillDefinitionEntity skill = skillDefinitionRepository
                .findByOrgIdAndSkillCode(auth.credential().getOrgId(), code)
                .orElseThrow(() -> new AgentOpenApiException(HttpStatus.FORBIDDEN, "skill_not_allowed", "activeSkillCode must be bound to this Agent"));
        if (!skill.isEnabled() || !agentSkillBindingRepository.existsByOrgIdAndAgentIdAndSkillIdAndEnabledTrue(
                auth.credential().getOrgId(),
                auth.credential().getAgentId(),
                skill.getId())) {
            throw new AgentOpenApiException(HttpStatus.FORBIDDEN, "skill_not_allowed", "activeSkillCode must be bound to this Agent");
        }
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int elapsedMs(Instant start, Instant end) {
        return (int) Math.max(0L, Duration.between(start, end).toMillis());
    }

    public record ChatCommand(
            String sessionId,
            String message,
            Map<String, Object> externalUser,
            List<String> knowledgeBaseIds,
            String activeSkillCode,
            Map<String, Object> metadata,
            CloudccContext cloudccContext
    ) {
    }

    public record CloudccContext(String accessToken, String baseUrl, String setupSvc) {
    }

    public record ChatExecution(
            AgentOpenApiAuthService.AuthenticatedCredential auth,
            AgentOpenApiSessionService.SessionResolution session,
            String requestId,
            String traceId,
            String answer,
            int elapsedMs,
            Map<String, Object> payload
    ) {
    }

    private record ModelChoice(String providerCode, String modelName) {
    }
}
