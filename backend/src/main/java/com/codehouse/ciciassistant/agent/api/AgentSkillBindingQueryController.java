package com.codehouse.ciciassistant.agent.api;

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

    public AgentSkillBindingQueryController(AgentSkillBindingService agentSkillBindingService) {
        this.agentSkillBindingService = agentSkillBindingService;
    }

    @GetMapping("/{agentId}/skills")
    public ApiResponse<Map<String, Object>> listMyAgentSkillBindings(@PathVariable String agentId) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(Map.of(
                "bindings", agentSkillBindingService.listBindings(orgId, agentId)
        ));
    }
}
