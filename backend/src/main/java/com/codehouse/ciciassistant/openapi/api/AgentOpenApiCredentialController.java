package com.codehouse.ciciassistant.openapi.api;

import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.openapi.service.AgentOpenApiCredentialService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agents/{agentId}/api-keys")
public class AgentOpenApiCredentialController {

    private final AgentOpenApiCredentialService credentialService;
    private final AgentAccessControlService accessControlService;

    public AgentOpenApiCredentialController(AgentOpenApiCredentialService credentialService,
                                            AgentAccessControlService accessControlService) {
        this.credentialService = credentialService;
        this.accessControlService = accessControlService;
    }

    @GetMapping
    public ApiResponse<List<AgentOpenApiCredentialService.CredentialView>> list(@PathVariable String agentId) {
        requireOpenApi(agentId);
        return ApiResponse.ok(credentialService.list(TenantContext.requireOrgId(), agentId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@PathVariable String agentId,
                                                   @Valid @RequestBody CreateCredentialRequest request) {
        requireOpenApi(agentId);
        AgentOpenApiCredentialService.CredentialCreation created = credentialService.create(
                TenantContext.requireOrgId(),
                agentId,
                TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context")),
                new AgentOpenApiCredentialService.CreateCredentialCommand(
                        request.name(),
                        request.runAsUserId(),
                        request.expiresAt(),
                        request.allowedIps(),
                        request.rateLimitPerMinute(),
                        request.dailyQuota(),
                        request.maxPromptChars(),
                        request.maxResponseChars(),
                        request.allowStream(),
                        request.allowTraceRead(),
                        request.scopes(),
                        request.keyType()));
        return ApiResponse.ok(
                credentialService.toCreationPayload(created),
                "API key created. Store the plain key now.");
    }

    @PutMapping("/{credentialId}")
    public ApiResponse<AgentOpenApiCredentialService.CredentialView> update(@PathVariable String agentId,
                                                                            @PathVariable Long credentialId,
                                                                            @Valid @RequestBody UpdateCredentialRequest request) {
        requireOpenApi(agentId);
        return ApiResponse.ok(credentialService.update(
                TenantContext.requireOrgId(),
                agentId,
                credentialId,
                new AgentOpenApiCredentialService.UpdateCredentialCommand(
                        request.name(),
                        request.runAsUserId(),
                        request.expiresAt(),
                        request.allowedIps(),
                        request.rateLimitPerMinute(),
                        request.dailyQuota(),
                        request.maxPromptChars(),
                        request.maxResponseChars(),
                        request.allowStream(),
                        request.allowTraceRead(),
                        request.status(),
                        request.scopes())));
    }

    @PostMapping("/{credentialId}/rotate")
    public ApiResponse<Map<String, Object>> rotate(@PathVariable String agentId, @PathVariable Long credentialId) {
        requireOpenApi(agentId);
        AgentOpenApiCredentialService.CredentialCreation rotated = credentialService.rotate(
                TenantContext.requireOrgId(),
                agentId,
                credentialId);
        return ApiResponse.ok(
                credentialService.toCreationPayload(rotated),
                "API key rotated. Store the plain key now.");
    }

    @PostMapping("/{credentialId}/revoke")
    public ApiResponse<AgentOpenApiCredentialService.CredentialView> revoke(@PathVariable String agentId,
                                                                            @PathVariable Long credentialId) {
        requireOpenApi(agentId);
        return ApiResponse.ok(credentialService.revoke(
                TenantContext.requireOrgId(),
                agentId,
                credentialId,
                TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"))));
    }

    private void requireOpenApi(String agentId) {
        accessControlService.require(
                TenantContext.requireOrgId(),
                TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context")),
                TenantContext.getRoles(),
                agentId,
                AgentPermission.OPENAPI);
    }

    public record CreateCredentialRequest(
            @NotBlank String name,
            @NotBlank String runAsUserId,
            Instant expiresAt,
            List<String> allowedIps,
            Integer rateLimitPerMinute,
            Integer dailyQuota,
            Integer maxPromptChars,
            Integer maxResponseChars,
            Boolean allowStream,
            Boolean allowTraceRead,
            List<String> scopes,
            String keyType
    ) {
    }

    public record UpdateCredentialRequest(
            String name,
            String runAsUserId,
            Instant expiresAt,
            List<String> allowedIps,
            Integer rateLimitPerMinute,
            Integer dailyQuota,
            Integer maxPromptChars,
            Integer maxResponseChars,
            Boolean allowStream,
            Boolean allowTraceRead,
            String status,
            List<String> scopes
    ) {
    }
}
