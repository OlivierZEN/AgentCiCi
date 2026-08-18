package com.codehouse.ciciassistant.openapi.service;

import com.codehouse.ciciassistant.openapi.domain.AgentApiSessionMapEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiSessionMapRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
        String hashInput = companyId + "|" + credentialId + "|" + agentId + "|" + externalSessionId;
        return UUID.nameUUIDFromBytes(hashInput.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public record SessionResolution(
            String externalSessionId,
            String internalSessionId,
            boolean stable
    ) {
    }
}
