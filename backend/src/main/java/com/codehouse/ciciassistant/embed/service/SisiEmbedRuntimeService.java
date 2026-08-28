package com.codehouse.ciciassistant.embed.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.ai.service.ChatAttachmentService;
import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.codehouse.ciciassistant.auth.ProductThemeCodes;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.embed.domain.SisiEmbedSessionEntity;
import com.codehouse.ciciassistant.embed.domain.SisiEmbedSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class SisiEmbedRuntimeService {

    private final SisiEmbedSessionRepository sessionRepository;
    private final ChatOrchestratorService chatOrchestratorService;
    private final ChatAttachmentService attachmentService;
    private final UserRepository userRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final ObjectMapper objectMapper;

    public SisiEmbedRuntimeService(SisiEmbedSessionRepository sessionRepository,
                                   ChatOrchestratorService chatOrchestratorService,
                                   ChatAttachmentService attachmentService,
                                   UserRepository userRepository,
                                   AgentDefinitionRepository agentDefinitionRepository,
                                   ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.chatOrchestratorService = chatOrchestratorService;
        this.attachmentService = attachmentService;
        this.userRepository = userRepository;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> createSession(EmbedTokenService.AuthenticatedEmbedToken token) {
        requirePermission(token, "chat:read");
        requireSisi(token);
        String sourceKey = sourceKey(token);
        SisiEmbedSessionEntity existing = sessionRepository
                .findByCompanyIdAndAgentIdAndExternalTenantIdAndExternalUserIdAndSourceAndObjectTypeAndObjectId(
                        token.companyId(), token.agentId(), token.externalTenantId(), token.externalUserId(),
                        token.source(), token.objectType(), token.objectId())
                .orElse(null);
        if (existing != null) {
            assertIdentity(existing, token);
            existing.touch(token.parentOrigin(), toJson(token.context()), token.recordName(), token.customerName());
            return view(existing, token);
        }

        Map<String, Object> chatSession = chatOrchestratorService.createEmbeddedSession(
                token.companyId(), token.userId(), sourceKey, token.agentId());
        String chatSessionId = String.valueOf(chatSession.get("id"));
        SisiEmbedSessionEntity created = new SisiEmbedSessionEntity(
                chatSessionId,
                token.companyId(),
                chatSessionId,
                token.userId(),
                token.agentId(),
                token.externalTenantId(),
                token.externalUserId(),
                token.source(),
                token.objectType(),
                token.objectId(),
                token.recordName(),
                token.customerName(),
                token.parentOrigin(),
                toJson(token.context()));
        try {
            sessionRepository.saveAndFlush(created);
            return view(created, token);
        } catch (DataIntegrityViolationException race) {
            SisiEmbedSessionEntity winner = sessionRepository
                    .findByCompanyIdAndAgentIdAndExternalTenantIdAndExternalUserIdAndSourceAndObjectTypeAndObjectId(
                            token.companyId(), token.agentId(), token.externalTenantId(), token.externalUserId(),
                            token.source(), token.objectType(), token.objectId())
                    .orElseThrow(() -> race);
            assertIdentity(winner, token);
            return view(winner, token);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> messages(EmbedTokenService.AuthenticatedEmbedToken token, String sessionId) {
        requirePermission(token, "chat:read");
        SisiEmbedSessionEntity session = requireSession(token, sessionId);
        return Map.of(
                "sessionId", session.getChatSessionId(),
                "messages", chatOrchestratorService.embeddedSessionMessages(
                        token.companyId(), token.userId(), session.getChatSessionId(), sourceKey(token)));
    }

    public void stream(EmbedTokenService.AuthenticatedEmbedToken token,
                       String sessionId,
                       ChatCommand command,
                       SseEmitter emitter) {
        requirePermission(token, "chat:write");
        SisiEmbedSessionEntity session = requireSession(token, sessionId);
        if (command == null || command.question() == null || command.question().trim().isBlank()) {
            throw new IllegalArgumentException("question is required");
        }
        chatOrchestratorService.chatStreamEmbedded(
                token.companyId(),
                token.userId(),
                session.getChatSessionId(),
                sourceKey(token),
                command.question().trim(),
                session.getAgentId(),
                command.attachmentIds(),
                trustedContext(token),
                emitter);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listAttachments(EmbedTokenService.AuthenticatedEmbedToken token, String sessionId) {
        requirePermission(token, "chat:read");
        SisiEmbedSessionEntity session = requireSession(token, sessionId);
        return attachmentService.list(token.companyId(), token.userId(), session.getChatSessionId());
    }

    @Transactional
    public Map<String, Object> uploadAttachment(EmbedTokenService.AuthenticatedEmbedToken token,
                                                String sessionId,
                                                String clientAttachmentId,
                                                MultipartFile file) {
        requirePermission(token, "attachment:write");
        SisiEmbedSessionEntity session = requireSession(token, sessionId);
        return attachmentService.upload(
                token.companyId(), token.userId(), session.getChatSessionId(), clientAttachmentId, file);
    }

    @Transactional(readOnly = true)
    public ChatAttachmentService.AttachmentContent attachmentContent(
            EmbedTokenService.AuthenticatedEmbedToken token, String sessionId, String attachmentId) {
        requirePermission(token, "chat:read");
        SisiEmbedSessionEntity session = requireSession(token, sessionId);
        return attachmentService.content(token.companyId(), token.userId(), session.getChatSessionId(), attachmentId);
    }

    @Transactional
    public Map<String, Object> deleteAttachment(EmbedTokenService.AuthenticatedEmbedToken token,
                                                String sessionId,
                                                String attachmentId) {
        requirePermission(token, "attachment:write");
        SisiEmbedSessionEntity session = requireSession(token, sessionId);
        return attachmentService.delete(token.companyId(), token.userId(), session.getChatSessionId(), attachmentId);
    }

    private SisiEmbedSessionEntity requireSession(EmbedTokenService.AuthenticatedEmbedToken token, String sessionId) {
        requireSisi(token);
        SisiEmbedSessionEntity session = sessionRepository.findByChatSessionIdAndCompanyId(sessionId, token.companyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        assertIdentity(session, token);
        chatOrchestratorService.assertEmbeddedSessionAccess(
                token.companyId(), token.userId(), session.getChatSessionId(), sourceKey(token));
        return session;
    }

    private void assertIdentity(SisiEmbedSessionEntity session, EmbedTokenService.AuthenticatedEmbedToken token) {
        if (!token.userId().equals(session.getInternalUserId())
                || !token.agentId().equals(session.getAgentId())
                || !token.externalTenantId().equals(session.getExternalTenantId())
                || !token.externalUserId().equals(session.getExternalUserId())
                || !token.source().equals(session.getSource())
                || !token.objectType().equals(session.getObjectType())
                || !token.objectId().equals(session.getObjectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
    }

    private Map<String, Object> view(SisiEmbedSessionEntity session, EmbedTokenService.AuthenticatedEmbedToken token) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", session.getChatSessionId());
        data.put("appCode", "sisi");
        String assistantName = String.valueOf(token.context().getOrDefault("assistantName", "")).trim();
        data.put("productName", assistantName.isBlank() ? "思思" : assistantName);
        data.put("agentAvatarBase64", websiteAgentAvatar(token));
        data.put("agentId", session.getAgentId());
        data.put("source", token.source());
        data.put("externalTenantId", session.getExternalTenantId());
        data.put("externalUserId", session.getExternalUserId());
        data.put("parentOrigin", token.parentOrigin());
        data.put("permissions", token.permissions());
        data.put("context", token.context());
        data.put("recordName", session.getRecordName() == null ? "" : session.getRecordName());
        data.put("customerName", session.getCustomerName() == null ? "" : session.getCustomerName());
        data.put("themeCode", userRepository.findByIdAndCompany_Id(token.userId(), token.companyId())
                .map(UserEntity::getAccount)
                .map(account -> ProductThemeCodes.normalizeStored(account.getThemeCode()))
                .orElse(ProductThemeCodes.DEFAULT));
        data.put("updatedAt", session.getUpdatedAt().toString());
        return data;
    }

    private String websiteAgentAvatar(EmbedTokenService.AuthenticatedEmbedToken token) {
        if (!"website".equals(token.source())) {
            return "";
        }
        return agentDefinitionRepository
                .findByCompanyIdAndAgentIdAndEnabledTrue(token.companyId(), token.agentId())
                .map(AgentDefinitionEntity::getAvatarBase64)
                .map(String::trim)
                .orElse("");
    }

    private Map<String, Object> trustedContext(EmbedTokenService.AuthenticatedEmbedToken token) {
        Map<String, Object> trusted = new LinkedHashMap<>();
        trusted.put("source", token.source());
        trusted.put("externalTenantId", token.externalTenantId());
        trusted.put("externalUserId", token.externalUserId());
        trusted.put("objectType", token.objectType());
        trusted.put("objectId", token.objectId());
        trusted.put("recordName", token.recordName());
        trusted.put("customerName", token.customerName());
        trusted.put("context", token.context());
        return trusted;
    }

    private String sourceKey(EmbedTokenService.AuthenticatedEmbedToken token) {
        String canonical = String.join("\n",
                token.companyId(), token.agentId(), token.externalTenantId(), token.externalUserId(),
                token.source(), token.objectType(), token.objectId());
        try {
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
            return "sisi:" + digest.substring(0, 40);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive embedded conversation key", exception);
        }
    }

    private void requireSisi(EmbedTokenService.AuthenticatedEmbedToken token) {
        if (!"sisi".equals(token.appCode())) {
            throw new ForbiddenException("Embed token appCode mismatch");
        }
    }

    private void requirePermission(EmbedTokenService.AuthenticatedEmbedToken token, String permission) {
        if (!token.can(permission)) {
            throw new ForbiddenException("Embed token is missing permission: " + permission);
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Embedded context is invalid", exception);
        }
    }

    public record ChatCommand(String question, List<String> attachmentIds) {
    }
}
