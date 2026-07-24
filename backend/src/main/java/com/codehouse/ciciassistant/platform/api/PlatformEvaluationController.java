package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.agent.service.AgentEvaluationControlPlaneService;
import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/platform/evaluation")
@RequirePlatformRole
public class PlatformEvaluationController {

    private final AgentEvaluationControlPlaneService service;

    public PlatformEvaluationController(AgentEvaluationControlPlaneService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR, RoleCodes.PLATFORM_AUDITOR})
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(service.platformOverview());
    }

    @GetMapping("/suites")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR, RoleCodes.PLATFORM_AUDITOR})
    public ApiResponse<List<Map<String, Object>>> suites() {
        return ApiResponse.ok(service.platformSuites());
    }

    @PostMapping("/suites")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> createSuite(@Valid @RequestBody SuiteRequest request) {
        return ApiResponse.ok(service.createPlatformSuite(suiteCommand(request), actorId(), actorRole()));
    }

    @PutMapping("/suites/{suiteId}")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> updateSuite(@PathVariable Long suiteId,
                                                        @Valid @RequestBody SuiteRequest request) {
        return ApiResponse.ok(service.updatePlatformSuite(suiteId, suiteCommand(request), actorId(), actorRole()));
    }

    @PostMapping("/suites/{suiteId}/publish")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> publishSuite(@PathVariable Long suiteId) {
        return ApiResponse.ok(service.publishPlatformSuite(suiteId, actorId(), actorRole()));
    }

    @PostMapping("/suites/{suiteId}/archive")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> archiveSuite(@PathVariable Long suiteId) {
        return ApiResponse.ok(service.archivePlatformSuite(suiteId, actorId(), actorRole()));
    }

    @GetMapping("/suites/{suiteId}/cases")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<List<Map<String, Object>>> cases(@PathVariable Long suiteId) {
        return ApiResponse.ok(service.platformCases(suiteId));
    }

    @PostMapping("/suites/{suiteId}/cases")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> createCase(@PathVariable Long suiteId,
                                                       @Valid @RequestBody CaseRequest request) {
        return ApiResponse.ok(service.addPlatformCase(
                suiteId,
                new AgentEvaluationControlPlaneService.CaseMutationCommand(
                        request.name(), request.inputText(), request.assertionType(), request.expectedText(),
                        request.forbiddenText(), request.expectedStatus(), request.requiredToolName(),
                        request.forbiddenToolName(), request.priority(), request.caseKey(), request.category(),
                        request.conversationHistoryJson(), request.fixtureJson(), request.assertionConfigJson(),
                        request.judgeConfigJson(), request.tagsJson(), request.reviewStatus(),
                        request.redactionStatus(), request.hiddenCase()),
                actorId(), actorRole()));
    }

    @PutMapping("/suites/{suiteId}/cases/{caseId}")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> updateCase(@PathVariable Long suiteId,
                                                       @PathVariable Long caseId,
                                                       @Valid @RequestBody CaseRequest request) {
        return ApiResponse.ok(service.updatePlatformCase(
                suiteId,
                caseId,
                new AgentEvaluationControlPlaneService.CaseMutationCommand(
                        request.name(), request.inputText(), request.assertionType(), request.expectedText(),
                        request.forbiddenText(), request.expectedStatus(), request.requiredToolName(),
                        request.forbiddenToolName(), request.priority(), request.caseKey(), request.category(),
                        request.conversationHistoryJson(), request.fixtureJson(), request.assertionConfigJson(),
                        request.judgeConfigJson(), request.tagsJson(), request.reviewStatus(),
                        request.redactionStatus(), request.hiddenCase()),
                actorId(), actorRole()));
    }

    @PostMapping("/suites/{suiteId}/bindings")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<Map<String, Object>> bindSuite(@PathVariable Long suiteId,
                                                      @Valid @RequestBody BindingRequest request) {
        return ApiResponse.ok(service.bindPlatformSuite(
                suiteId,
                new AgentEvaluationControlPlaneService.BindingCommand(
                        request.companyId(), request.agentId(), request.appCode(), request.industryCode()),
                actorId(), actorRole()));
    }

    @GetMapping("/runs")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR, RoleCodes.PLATFORM_AUDITOR})
    public ApiResponse<List<Map<String, Object>>> runs() {
        return ApiResponse.ok(service.platformRuns());
    }

    private AgentEvaluationControlPlaneService.PlatformSuiteCommand suiteCommand(SuiteRequest request) {
        return new AgentEvaluationControlPlaneService.PlatformSuiteCommand(
                request.name(), request.description(), request.gateMode(), request.minPassRate(),
                request.scopeType(), request.visibility(), request.templateCode(), request.agentId(),
                request.appCode(), request.industryCode(), request.hiddenResults(), request.mandatory());
    }

    private String actorId() {
        return TenantContext.getUserId().orElse("platform-system");
    }

    private String actorRole() {
        return TenantContext.getRoles().stream()
                .filter(RoleCodes::isPlatformRole)
                .findFirst()
                .orElse(RoleCodes.PLATFORM_ADMIN);
    }

    public record SuiteRequest(
            @NotBlank String name,
            String description,
            String gateMode,
            Double minPassRate,
            String scopeType,
            String visibility,
            @NotBlank String templateCode,
            String agentId,
            String appCode,
            String industryCode,
            boolean hiddenResults,
            boolean mandatory
    ) {}

    public record CaseRequest(
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
            String redactionStatus,
            boolean hiddenCase
    ) {}

    public record BindingRequest(String companyId, String agentId, String appCode, String industryCode) {}
}
