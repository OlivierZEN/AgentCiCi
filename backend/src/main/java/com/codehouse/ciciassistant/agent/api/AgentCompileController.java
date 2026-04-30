package com.codehouse.ciciassistant.agent.api;

import com.codehouse.ciciassistant.agent.service.AgentCompileService;
import com.codehouse.ciciassistant.agent.service.AgentWorkflowRuntimeService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agents")
public class AgentCompileController {

    private final AgentCompileService agentCompileService;
    private final AgentWorkflowRuntimeService agentWorkflowRuntimeService;

    public AgentCompileController(AgentCompileService agentCompileService,
                                  AgentWorkflowRuntimeService agentWorkflowRuntimeService) {
        this.agentCompileService = agentCompileService;
        this.agentWorkflowRuntimeService = agentWorkflowRuntimeService;
    }

    @PostMapping("/compile")
    public ApiResponse<AgentCompileService.CompileResult> compile(@Valid @RequestBody CompileRequest request) {
        return doCompile(null, request);
    }

    @PostMapping("/{agentId}/compile")
    public ApiResponse<AgentCompileService.CompileResult> compileForAgent(@PathVariable String agentId,
                                                                          @Valid @RequestBody CompileRequest request) {
        return doCompile(agentId, request);
    }

    @PostMapping("/{agentId}/debug")
    public ApiResponse<DebugResult> debugForAgent(@PathVariable String agentId,
                                                  @Valid @RequestBody DebugRequest request) {
        String orgId = TenantContext.requireOrgId();
        AgentWorkflowRuntimeService.DebugRuntimeResult runtimeResult = agentWorkflowRuntimeService.debug(
                orgId,
                agentId,
                request.input(),
                request.requestedKnowledgeBaseIds(),
                request.skillRefs()
        );
        return ApiResponse.ok(new DebugResult(
                runtimeResult.agentId(),
                runtimeResult.activeSkills(),
                runtimeResult.effectiveToolNames(),
                runtimeResult.agentDirectToolNames(),
                runtimeResult.skillDeclaredToolNames(),
                runtimeResult.skillScopedToolNames(),
                runtimeResult.effectiveKnowledgeBaseIds(),
                runtimeResult.warnings(),
                runtimeResult.traceSteps(),
                runtimeResult.runtimeSource(),
                runtimeResult.publishedVersionId(),
                runtimeResult.workflowCodePreview(),
                runtimeResult.resolvedSkillVersions(),
                runtimeResult.policyBundle(),
                runtimeResult.runtimeGovernanceNotes(),
                runtimeResult.executionStatus(),
                runtimeResult.executionOutput(),
                runtimeResult.executionTrace(),
                runtimeResult.contextSnapshot()
        ));
    }

    private ApiResponse<AgentCompileService.CompileResult> doCompile(String agentId, CompileRequest request) {
        String orgId = TenantContext.requireOrgId();
        AgentCompileService.CompileResult result = agentCompileService.compile(orgId, new AgentCompileService.CompileCommand(
                agentId,
                request.name(),
                request.summary(),
                request.greeting(),
                request.model(),
                request.systemPrompt(),
                request.specText(),
                request.channels(),
                request.knowledgeBaseIds(),
                request.toolIds(),
                request.skillRefs(),
                request.handoffRule(),
                request.safetyLevel(),
                request.executionMode(),
                request.version()
        ));
        return ApiResponse.ok(result);
    }

    public record CompileRequest(
            @NotBlank String name,
            String summary,
            String greeting,
            @NotBlank String model,
            String systemPrompt,
            @NotBlank String specText,
            List<String> channels,
            List<Long> knowledgeBaseIds,
            List<String> toolIds,
            List<String> skillRefs,
            String handoffRule,
            String safetyLevel,
            String executionMode,
            String version
    ) {
    }

    public record DebugRequest(
            String input,
            List<String> requestedKnowledgeBaseIds,
            List<String> skillRefs
    ) {
    }

    public record DebugResult(
            String agentId,
            List<String> activeSkills,
            List<String> effectiveToolNames,
            List<String> agentDirectToolNames,
            List<String> skillDeclaredToolNames,
            List<String> skillScopedToolNames,
            List<String> effectiveKnowledgeBaseIds,
            List<String> warnings,
            List<String> traceSteps,
            String runtimeSource,
            Long publishedVersionId,
            String workflowCodePreview,
            List<AgentWorkflowRuntimeService.RuntimeSkillGovernanceView> resolvedSkillVersions,
            AgentWorkflowRuntimeService.RuntimePolicyBundleView policyBundle,
            List<String> runtimeGovernanceNotes,
            String executionStatus,
            String executionOutput,
            List<String> executionTrace,
            Map<String, Object> contextSnapshot
    ) {
    }
}
