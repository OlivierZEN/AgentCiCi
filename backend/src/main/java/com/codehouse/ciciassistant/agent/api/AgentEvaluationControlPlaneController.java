package com.codehouse.ciciassistant.agent.api;

import com.codehouse.ciciassistant.agent.service.AgentEvaluationControlPlaneService;
import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/evaluation")
@RequireOrgAdmin
public class AgentEvaluationControlPlaneController {

    private final AgentEvaluationControlPlaneService service;

    public AgentEvaluationControlPlaneController(AgentEvaluationControlPlaneService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(service.tenantOverview(TenantContext.requireOrgId()));
    }

    @GetMapping("/suites")
    public ApiResponse<List<Map<String, Object>>> suites(
            @RequestParam(name = "agentId", required = false) String agentId) {
        return ApiResponse.ok(service.tenantSuites(TenantContext.requireOrgId(), agentId));
    }

    @PostMapping("/suites")
    public ApiResponse<Map<String, Object>> createSuite(@Valid @RequestBody TenantSuiteRequest request) {
        return ApiResponse.ok(service.createTenantSuite(
                TenantContext.requireOrgId(),
                request.agentId(),
                new AgentEvaluationControlPlaneService.TenantSuiteCommand(
                        request.name(), request.description(), request.gateMode(), request.minPassRate(),
                        request.templateCode(), request.appCode(), request.industryCode()),
                actorId()));
    }

    @GetMapping("/suites/{suiteId}/cases")
    public ApiResponse<List<Map<String, Object>>> cases(@PathVariable Long suiteId,
                                                        @RequestParam String agentId) {
        return ApiResponse.ok(service.tenantCases(TenantContext.requireOrgId(), agentId, suiteId));
    }

    @PostMapping("/suites/{suiteId}/cases")
    public ApiResponse<Map<String, Object>> createCase(@PathVariable Long suiteId,
                                                       @Valid @RequestBody CaseRequest request) {
        return ApiResponse.ok(service.addTenantCase(
                TenantContext.requireOrgId(), request.agentId(), suiteId, caseCommand(request, false)));
    }

    @PutMapping("/suites/{suiteId}/cases/{caseId}")
    public ApiResponse<Map<String, Object>> updateCase(@PathVariable Long suiteId,
                                                       @PathVariable Long caseId,
                                                       @Valid @RequestBody CaseRequest request) {
        return ApiResponse.ok(service.updateTenantCase(
                TenantContext.requireOrgId(), request.agentId(), suiteId, caseId, caseCommand(request, false)));
    }

    @PostMapping("/runs")
    public ApiResponse<Map<String, Object>> run(@Valid @RequestBody RunRequest request) {
        return ApiResponse.ok(service.runTenantSuite(
                TenantContext.requireOrgId(),
                request.agentId(),
                request.suiteId(),
                new AgentEvaluationControlPlaneService.RunSuiteCommand(
                        request.versionNo(), request.targetType(), request.baselineVersionNo(), request.triggerType()),
                actorId()));
    }

    @GetMapping("/runs")
    public ApiResponse<List<Map<String, Object>>> runs(
            @RequestParam(name = "agentId", required = false) String agentId) {
        return ApiResponse.ok(service.tenantRuns(TenantContext.requireOrgId(), agentId));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<Map<String, Object>> runDetail(@PathVariable Long runId) {
        return ApiResponse.ok(service.tenantRunDetail(TenantContext.requireOrgId(), runId));
    }

    @PostMapping("/cases/from-trace")
    public ApiResponse<Map<String, Object>> fromTrace(@Valid @RequestBody TraceCaseRequest request) {
        return ApiResponse.ok(service.createCaseFromTrace(
                TenantContext.requireOrgId(),
                actorId(),
                new AgentEvaluationControlPlaneService.TraceCaseCommand(
                        request.suiteId(), request.traceId(), request.agentId(), request.name(),
                        request.priority(), request.category())));
    }

    @GetMapping("/issues")
    public ApiResponse<List<Map<String, Object>>> issues() {
        return ApiResponse.ok(service.listIssues(TenantContext.requireOrgId()));
    }

    @PostMapping("/issues")
    public ApiResponse<Map<String, Object>> createIssue(@Valid @RequestBody IssueRequest request) {
        return ApiResponse.ok(service.createIssue(
                TenantContext.requireOrgId(),
                new AgentEvaluationControlPlaneService.IssueCommand(
                        request.agentId(), request.runId(), request.caseId(), request.title(),
                        request.rootCauseType(), request.severity(), request.description()),
                actorId()));
    }

    @PutMapping("/issues/{issueId}")
    public ApiResponse<Map<String, Object>> updateIssue(@PathVariable Long issueId,
                                                        @Valid @RequestBody IssueUpdateRequest request) {
        return ApiResponse.ok(service.updateIssue(
                TenantContext.requireOrgId(),
                issueId,
                new AgentEvaluationControlPlaneService.IssueUpdateCommand(
                        request.status(), request.rootCauseType(), request.severity(), request.ownerUserId(),
                        request.fixVersionNo(), request.verificationRunId(), request.description(), request.resolution())));
    }

    private AgentEvaluationControlPlaneService.CaseMutationCommand caseCommand(CaseRequest request, boolean hidden) {
        return new AgentEvaluationControlPlaneService.CaseMutationCommand(
                request.name(), request.inputText(), request.assertionType(), request.expectedText(),
                request.forbiddenText(), request.expectedStatus(), request.requiredToolName(),
                request.forbiddenToolName(), request.priority(), request.caseKey(), request.category(),
                request.conversationHistoryJson(), request.fixtureJson(), request.assertionConfigJson(),
                request.judgeConfigJson(), request.tagsJson(), request.reviewStatus(),
                request.redactionStatus(), hidden);
    }

    private String actorId() {
        return TenantContext.getUserId().orElse("org-admin");
    }

    public record TenantSuiteRequest(
            @NotBlank String agentId,
            @NotBlank String name,
            String description,
            String gateMode,
            Double minPassRate,
            String templateCode,
            String appCode,
            String industryCode
    ) {}

    public record CaseRequest(
            @NotBlank String agentId,
            @NotBlank String name,
            @NotBlank String inputText,
            String assertionType,
            String expectedText,
            String forbiddenText,
            String expectedStatus,
            String requiredToolName,
            String forbiddenToolName,
            String priority,
            String caseKey,
            String category,
            String conversationHistoryJson,
            String fixtureJson,
            String assertionConfigJson,
            String judgeConfigJson,
            String tagsJson,
            String reviewStatus,
            String redactionStatus
    ) {}

    public record RunRequest(
            @NotBlank String agentId,
            @NotNull Long suiteId,
            @NotNull Integer versionNo,
            String targetType,
            Integer baselineVersionNo,
            String triggerType
    ) {}

    public record TraceCaseRequest(
            @NotNull Long suiteId,
            @NotBlank String traceId,
            String agentId,
            String name,
            String priority,
            String category
    ) {}

    public record IssueRequest(
            @NotBlank String agentId,
            Long runId,
            Long caseId,
            @NotBlank String title,
            String rootCauseType,
            String severity,
            String description
    ) {}

    public record IssueUpdateRequest(
            String status,
            String rootCauseType,
            String severity,
            String ownerUserId,
            Integer fixVersionNo,
            Long verificationRunId,
            String description,
            String resolution
    ) {}
}
