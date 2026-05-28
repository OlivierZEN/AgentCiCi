package com.codehouse.ciciassistant.agent.api;

import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.agent.service.AgentSkillBindingService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/agents")
public class AgentSkillBindingQueryController {

    private final AgentSkillBindingService agentSkillBindingService;
    private final AgentAccessControlService accessControlService;

    public AgentSkillBindingQueryController(AgentSkillBindingService agentSkillBindingService,
                                            AgentAccessControlService accessControlService) {
        this.agentSkillBindingService = agentSkillBindingService;
        this.accessControlService = accessControlService;
    }

    @GetMapping("/{agentId}/skills")
    public ApiResponse<Map<String, Object>> listMyAgentSkillBindings(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        String userId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        accessControlService.require(orgId, userId, TenantContext.getRoles(), agentId, AgentPermission.RUN);
        return ApiResponse.ok(Map.of(
                "bindings", agentSkillBindingService.listBindings(orgId, agentId)
        ));
    }
}
