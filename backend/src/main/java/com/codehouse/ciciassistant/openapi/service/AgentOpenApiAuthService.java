package com.codehouse.ciciassistant.openapi.service;

import com.codehouse.ciciassistant.agent.domain.AgentChannelBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.openapi.config.AgentOpenApiProperties;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentOpenApiAuthService {

    private static final String API_KEY_HEADER = "X-Cici-Api-Key";

    private final AgentApiCredentialRepository credentialRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentChannelBindingRepository channelBindingRepository;
    private final AgentOpenApiProperties properties;
    private final AgentApiKeyGenerator keyGenerator;
    private final AgentOpenApiCredentialService credentialService;

    public AgentOpenApiAuthService(AgentApiCredentialRepository credentialRepository,
                                   AgentDefinitionRepository agentDefinitionRepository,
                                   AgentChannelBindingRepository channelBindingRepository,
                                   AgentOpenApiProperties properties,
                                   AgentApiKeyGenerator keyGenerator,
                                   AgentOpenApiCredentialService credentialService) {
        this.credentialRepository = credentialRepository;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.channelBindingRepository = channelBindingRepository;
        this.properties = properties;
        this.keyGenerator = keyGenerator;
        this.credentialService = credentialService;
    }

    @Transactional
    public AuthenticatedCredential authenticate(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            throw new AgentOpenApiException(HttpStatus.FORBIDDEN, "agent_open_api_disabled", "Agent Open API is disabled");
        }
        String plainKey = extractPlainKey(request);
        if (plainKey.isBlank()) {
            throw new AgentOpenApiException(HttpStatus.UNAUTHORIZED, "agent_api_key_missing", "API key is required");
        }
        String publicId = keyGenerator.publicIdFromPlainKey(plainKey);
        if (publicId.isBlank()) {
            throw new AgentOpenApiException(HttpStatus.UNAUTHORIZED, "agent_api_key_invalid", "API key is invalid or revoked");
        }
        AgentApiCredentialEntity credential = credentialRepository.findByPublicId(publicId)
                .filter(item -> keyGenerator.matches(plainKey, item.getKeyHash()))
                .orElseThrow(() -> new AgentOpenApiException(
                        HttpStatus.UNAUTHORIZED,
                        "agent_api_key_invalid",
                        "API key is invalid or revoked"));
        if (!AgentApiCredentialEntity.STATUS_ACTIVE.equals(credential.getStatus())) {
            throw new AgentOpenApiException(HttpStatus.UNAUTHORIZED, "agent_api_key_invalid", "API key is invalid or revoked");
        }
        if (credential.getExpiresAt() != null && credential.getExpiresAt().isBefore(Instant.now())) {
            throw new AgentOpenApiException(HttpStatus.FORBIDDEN, "agent_api_key_expired", "API key is expired");
        }
        if (!clientIpAllowed(credentialService.toView(credential).allowedIps(), clientIp(request))) {
            throw new AgentOpenApiException(HttpStatus.FORBIDDEN, "agent_api_ip_denied", "Client IP is not allowed");
        }
        AgentDefinitionEntity agent = agentDefinitionRepository
                .findByOrgIdAndAgentId(credential.getOrgId(), credential.getAgentId())
                .orElseThrow(() -> new AgentOpenApiException(HttpStatus.NOT_FOUND, "agent_not_found", "Agent not found"));
        if (!agent.isEnabled()) {
            throw new AgentOpenApiException(HttpStatus.NOT_FOUND, "agent_not_found", "Agent not found");
        }
        if (agent.getPublishedVersionId() == null) {
            throw new AgentOpenApiException(HttpStatus.CONFLICT, "agent_not_published", "Agent is not published");
        }
        if (!channelBindingRepository.existsByOrgIdAndAgentIdAndChannelIdAndEnabledTrue(
                credential.getOrgId(),
                credential.getAgentId(),
                "api")) {
            throw new AgentOpenApiException(HttpStatus.FORBIDDEN, "agent_channel_disabled", "Agent API channel is disabled");
        }
        credential.markUsed();
        return new AuthenticatedCredential(
                credential,
                agent,
                clientIp(request),
                credentialService.toView(credential));
    }

    private String extractPlainKey(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        String apiKey = request.getHeader(API_KEY_HEADER);
        return apiKey == null ? "" : apiKey.trim();
    }

    private boolean clientIpAllowed(List<String> allowedIps, String clientIp) {
        if (allowedIps == null || allowedIps.isEmpty()) {
            return true;
        }
        for (String entry : allowedIps) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String text = entry.trim();
            if (text.equals(clientIp)) {
                return true;
            }
            if (text.endsWith("/32") && text.substring(0, text.length() - 3).equals(clientIp)) {
                return true;
            }
        }
        return false;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }

    public void requireScope(AuthenticatedCredential auth, String scope) {
        String normalized = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return;
        }
        List<String> scopes = auth.credentialView().scopes();
        if (scopes == null || scopes.isEmpty() || scopes.contains("*")) {
            return;
        }
        boolean allowed = scopes.stream()
                .filter(item -> item != null)
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .anyMatch(item -> item.equals(normalized));
        if (!allowed) {
            throw new AgentOpenApiException(HttpStatus.FORBIDDEN, "agent_api_scope_denied", "API key scope does not allow this operation");
        }
    }

    public record AuthenticatedCredential(
            AgentApiCredentialEntity credential,
            AgentDefinitionEntity agent,
            String clientIp,
            AgentOpenApiCredentialService.CredentialView credentialView
    ) {
    }
}
