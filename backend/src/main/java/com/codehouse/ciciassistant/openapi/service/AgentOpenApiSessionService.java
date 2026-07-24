package com.codehouse.ciciassistant.openapi.service;

import com.codehouse.ciciassistant.openapi.domain.AgentApiSessionMapEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiSessionMapRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentOpenApiSessionService {

    private final AgentApiSessionMapRepository sessionMapRepository;

    public AgentOpenApiSessionService(AgentApiSessionMapRepository sessionMapRepository) {
        this.sessionMapRepository = sessionMapRepository;
    }

    @Transactional
    public SessionResolution resolve(AgentOpenApiAuthService.AuthenticatedCredential auth,
                                     String requestedSessionId,
                                     String externalUserId,
                                     String requestId) {
        String externalSessionId = normalizeSessionId(requestedSessionId, requestId);
        String internalSessionId = internalSessionId(
                auth.credential().getCompanyId(),
                auth.credential().getId(),
                auth.credential().getAgentId(),
                auth.credential().getPublicId(),
                externalSessionId);
        boolean stableExternalSession = requestedSessionId != null && !requestedSessionId.trim().isBlank();
        if (!stableExternalSession) {
            return new SessionResolution(externalSessionId, internalSessionId, false);
        }
        AgentApiSessionMapEntity entity = sessionMapRepository
                .findByCompanyIdAndCredentialIdAndAgentIdAndExternalSessionId(
                        auth.credential().getCompanyId(),
                        auth.credential().getId(),
                        auth.credential().getAgentId(),
                        externalSessionId)
                .orElseGet(() -> sessionMapRepository.save(new AgentApiSessionMapEntity(
                        auth.credential().getCompanyId(),
                        auth.credential().getId(),
                        auth.credential().getAgentId(),
                        externalSessionId,
                        internalSessionId,
                        externalUserId)));
        if (externalUserId != null
                && !externalUserId.isBlank()
                && !externalUserId.equals(entity.getExternalUserId())) {
            entity.updateExternalUserId(externalUserId);
        }
        return new SessionResolution(entity.getExternalSessionId(), entity.getInternalSessionId(), true);
    }

    private String normalizeSessionId(String requestedSessionId, String requestId) {
        String text = requestedSessionId == null ? "" : requestedSessionId.trim();
        if (text.isBlank()) {
            text = requestId;
        }
        if (text.length() > 160) {
            throw new AgentOpenApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid_request",
                    "sessionId must be 160 characters or fewer");
        }
        return text;
    }

    private String internalSessionId(String companyId,
                                     Long credentialId,
                                     String agentId,
                                     String publicId,
                                     String externalSessionId) {
        String publicPart = safePrefix(publicId, 12);
        String hashInput = companyId + "|" + credentialId + "|" + agentId + "|" + externalSessionId;
        return "api:" + publicPart + ":" + sha256Base64(hashInput).substring(0, 20);
    }

    private static String safePrefix(String value, int max) {
        String text = value == null ? "" : value.replaceAll("[^a-zA-Z0-9_-]", "");
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    private static String sha256Base64(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to map Agent Open API session", ex);
        }
    }

    public record SessionResolution(
            String externalSessionId,
            String internalSessionId,
            boolean stable
    ) {
    }
}
