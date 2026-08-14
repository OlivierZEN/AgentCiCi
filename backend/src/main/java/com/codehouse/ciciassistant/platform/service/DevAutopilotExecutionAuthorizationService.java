package com.codehouse.ciciassistant.platform.service;

import com.codehouse.ciciassistant.agent.service.AgentServicePrincipalExecutionService;
import com.codehouse.ciciassistant.auth.service.OfficialAccessTokenService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Converts an authorized HUMAN confirmation into one short-lived product-manager SERVICE
 * execution authorization. The operation whitelist is deliberately fixed here so callers
 * cannot choose arbitrary Semattice scopes or a different machine principal.
 */
@Service
public class DevAutopilotExecutionAuthorizationService {
    private final DevAutopilotTenantApplicationService applications;
    private final AgentServicePrincipalExecutionService executionPrincipals;

    public DevAutopilotExecutionAuthorizationService(DevAutopilotTenantApplicationService applications,
                                                      AgentServicePrincipalExecutionService executionPrincipals) {
        this.applications = applications;
        this.executionPrincipals = executionPrincipals;
    }

    public AuthorizationView authorize(String companyId, String memberId, Operation operation) {
        DevAutopilotTenantApplicationService.View activation = applications.get(companyId);
        if (!activation.enabled() || !"ACTIVE".equals(activation.actualState())) {
            throw new ForbiddenException("当前租户尚未开通或已暂停 DevAutopilot");
        }
        DevAutopilotTenantApplicationService.ResourceView productManagerAgent = activation.resources().stream()
                .filter(resource -> "product_manager".equals(resource.logicalRole()))
                .filter(resource -> "AGENT".equals(resource.resourceType()))
                .filter(DevAutopilotTenantApplicationService.ResourceView::primary)
                .filter(resource -> "ACTIVE".equals(resource.lifecycleState()))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("DevAutopilot 产品经理智能体不可用"));
        String expectedPrincipalId = activation.resources().stream()
                .filter(resource -> "product_manager".equals(resource.logicalRole()))
                .filter(resource -> "SERVICE_PRINCIPAL".equals(resource.resourceType()))
                .filter(DevAutopilotTenantApplicationService.ResourceView::primary)
                .filter(resource -> "ACTIVE".equals(resource.lifecycleState()))
                .map(DevAutopilotTenantApplicationService.ResourceView::externalId)
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("DevAutopilot 产品经理机器身份不可用"));

        Operation selected = operation == null ? Operation.UNKNOWN : operation;
        List<String> scopes = switch (selected) {
            case REQUIREMENT_CONFIRM -> List.of("runtime.record.read", "runtime.record.update");
            case TASK_PLAN_CONFIRM -> List.of("runtime.record.read", "runtime.record.create");
            case TASK_REVIEW -> List.of(
                    "identity.principal.sync",
                    "runtime.record.read",
                    "runtime.record.create",
                    "runtime.record.update");
            case UNKNOWN -> throw new IllegalArgumentException("不支持的 DevAutopilot 委托操作");
        };
        AgentServicePrincipalExecutionService.ExecutionAuthorization authorization =
                executionPrincipals.authorizeSemattice(
                        companyId, memberId, productManagerAgent.externalId(), scopes,
                        "devautopilot_" + selected.name().toLowerCase());
        if (!expectedPrincipalId.equals(authorization.servicePrincipalId())) {
            throw new ForbiddenException("DevAutopilot 产品经理执行身份与应用资源不一致");
        }
        OfficialAccessTokenService.IssuedToken token = authorization.token();
        if (!companyId.equals(token.companyId()) || !activation.sematticeTenantId().equals(token.tenantId())
                || !token.scopes().containsAll(scopes)) {
            throw new ForbiddenException("DevAutopilot 产品经理执行授权上下文不一致");
        }
        return new AuthorizationView(
                token.token(), token.expiresAt(), token.tenantId(), token.companyId(), token.scopes(),
                authorization.servicePrincipalId(), authorization.delegatedByPrincipalId(),
                authorization.delegationPolicy(), authorization.effectiveAppRole());
    }

    public enum Operation {
        REQUIREMENT_CONFIRM,
        TASK_PLAN_CONFIRM,
        TASK_REVIEW,
        UNKNOWN
    }

    public record AuthorizationView(String accessToken,
                                    Instant expiresAt,
                                    String tenantId,
                                    String companyId,
                                    List<String> scopes,
                                    String servicePrincipalId,
                                    String delegatedByPrincipalId,
                                    String delegationPolicy,
                                    String effectiveAppRole) {
    }
}
