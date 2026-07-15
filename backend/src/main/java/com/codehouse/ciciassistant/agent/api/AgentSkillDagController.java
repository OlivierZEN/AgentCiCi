package com.codehouse.ciciassistant.agent.api;

import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.agent.service.SkillDependencyGraphService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agents")
public class AgentSkillDagController {

    private final SkillDependencyGraphService graphService;
    private final AgentAccessControlService accessControlService;
    private final AgentDefinitionService agentDefinitionService;

    public AgentSkillDagController(SkillDependencyGraphService graphService,
                                   AgentAccessControlService accessControlService,
                                   AgentDefinitionService agentDefinitionService) {
        this.graphService = graphService;
        this.accessControlService = accessControlService;
        this.agentDefinitionService = agentDefinitionService;
    }

    @GetMapping("/{agentId}/skill-dag")
    public ApiResponse<SkillDependencyGraphService.GraphView> getSkillDag(
            @PathVariable String agentId,
            @RequestParam(required = false) Integer versionNo) {
        if (TenantContext.getTokenType().filter("platform"::equals).isPresent()) {
            throw new ForbiddenException("需要组织账号权限");
        }
        String orgId = TenantContext.requireOrgId();
        agentDefinitionService.warmupBuiltinAgents(orgId);
        String userId = TenantContext.getUserId()
                .orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        accessControlService.require(
                orgId,
                userId,
                TenantContext.getRoles(),
                agentId,
                AgentPermission.VIEW);
        return ApiResponse.ok(graphService.getAgentGraph(orgId, agentId, versionNo));
    }
}
