package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.agent.service.SkillDependencyGraphService;
import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.config.PlatformAccountProperties;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.codehouse.ciciassistant.platform.service.PlatformGovernanceService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
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
@RequestMapping("/platform")
@RequirePlatformRole
public class PlatformController {

    private final PlatformGovernanceService platformGovernanceService;
    private final PlatformAuditService platformAuditService;
    private final PlatformAccountProperties platformAccountProperties;
    private final SkillDependencyGraphService skillDependencyGraphService;

    public PlatformController(PlatformGovernanceService platformGovernanceService,
                              PlatformAuditService platformAuditService,
                              PlatformAccountProperties platformAccountProperties,
                              SkillDependencyGraphService skillDependencyGraphService) {
        this.platformGovernanceService = platformGovernanceService;
        this.platformAuditService = platformAuditService;
        this.platformAccountProperties = platformAccountProperties;
        this.skillDependencyGraphService = skillDependencyGraphService;
    }

    @GetMapping("/bootstrap")
    public ApiResponse<Map<String, Object>> bootstrap() {
        String companyId = platformScopeId();
        platformGovernanceService.ensurePlatformAssets(companyId);
        List<PlatformGovernanceService.PlatformSkillView> platformSkills = platformGovernanceService.listPlatformSkills(companyId);
        List<PlatformGovernanceService.PlatformToolView> platformTools = platformGovernanceService.listPlatformTools(companyId);
        PlatformGovernanceService.PlatformPolicyBundleView corePolicyBundle =
                platformGovernanceService.getCorePolicyBundleSummary(companyId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roles", TenantContext.getRoles());
        payload.put("skillCount", platformSkills.size());
        payload.put("hiddenSkillCount", platformSkills.stream().filter(item -> "HIDDEN".equals(item.visibility())).count());
        payload.put("builtinToolCount", platformTools.size());
        payload.put("recentAuditCount", platformAuditService.latest(companyId).size());
        payload.put("policyBundleCode", corePolicyBundle.bundleCode());
        payload.put("policyBundleVersionNo", corePolicyBundle.versionNo());
        payload.put("policyBundleLivePublishedAgentCount", corePolicyBundle.livePublishedAgentCount());
        return ApiResponse.ok(payload);
    }

    @GetMapping("/skills")
    public ApiResponse<List<PlatformGovernanceService.PlatformSkillView>> listPlatformSkills() {
        String companyId = platformScopeId();
        return ApiResponse.ok(platformGovernanceService.listPlatformSkills(companyId));
    }

    @GetMapping("/skills/{id}/versions")
    public ApiResponse<List<PlatformGovernanceService.PlatformSkillVersionView>> listPlatformSkillVersions(
            @PathVariable Long id) {
        String companyId = platformScopeId();
        return ApiResponse.ok(platformGovernanceService.listPlatformSkillVersions(companyId, id));
    }

    @GetMapping("/skills/{id}/dependency-graph")
    public ApiResponse<SkillDependencyGraphService.GraphView> skillDependencyGraph(@PathVariable Long id) {
        return ApiResponse.ok(skillDependencyGraphService.getSkillImpactGraph(platformScopeId(), id));
    }

    @GetMapping("/policies/core")
    public ApiResponse<PlatformGovernanceService.PlatformPolicyBundleView> getCorePolicyBundle() {
        String companyId = platformScopeId();
        return ApiResponse.ok(platformGovernanceService.getCorePolicyBundleSummary(companyId));
    }

    @GetMapping("/policies/core/versions")
    public ApiResponse<List<PlatformGovernanceService.PlatformPolicyBundleVersionView>> listCorePolicyBundleVersions() {
        String companyId = platformScopeId();
        return ApiResponse.ok(platformGovernanceService.listCorePolicyBundleVersions(companyId));
    }

    @PostMapping("/policies/core/versions")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformGovernanceService.PlatformPolicyBundleView> createCorePolicyBundleDraft(
            @Valid @RequestBody PolicyBundleDraftRequest request) {
        String companyId = platformScopeId();
        return ApiResponse.ok(platformGovernanceService.saveCorePolicyBundleDraft(companyId,
                new PlatformGovernanceService.PolicyBundleDraftCommand(
                        request.name(),
                        request.description(),
                        request.promptFragment(),
                        request.handoffRules(),
                        request.sourceSkillCodes()
                )));
    }

    @PostMapping("/policies/core/publish")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformGovernanceService.PlatformPolicyBundleView> publishCorePolicyBundle(
            @Valid @RequestBody PolicyBundlePublishRequest request) {
        String companyId = platformScopeId();
        return ApiResponse.ok(platformGovernanceService.publishCorePolicyBundleVersion(companyId, request.versionNo()));
    }

    @PostMapping("/policies/core/rollback")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformGovernanceService.PlatformPolicyBundleView> rollbackCorePolicyBundle(
            @Valid @RequestBody PolicyBundlePublishRequest request) {
        String companyId = platformScopeId();
        return ApiResponse.ok(platformGovernanceService.rollbackCorePolicyBundleVersion(companyId, request.versionNo()));
    }

    @PostMapping("/skills/{id}/versions")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformGovernanceService.PlatformSkillView> createPlatformSkillDraft(
            @PathVariable Long id,
            @Valid @RequestBody PlatformSkillDraftRequest request) {
        String companyId = platformScopeId();
        return ApiResponse.ok(platformGovernanceService.savePlatformSkillDraft(companyId, id,
                new PlatformGovernanceService.SkillTemplateDraftCommand(
                        request.name(),
                        request.description(),
                        request.promptFragment(),
                        request.toolWhitelist(),
                        request.kbWhitelist(),
                        request.handoffRule(),
                        request.outputContract(),
                        request.riskLevel(),
                        request.changelog()
                )));
    }

    @PostMapping("/skills/{id}/publish")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformGovernanceService.PlatformSkillView> publishPlatformSkillVersion(
            @PathVariable Long id,
            @Valid @RequestBody PlatformSkillPublishRequest request) {
        String companyId = platformScopeId();
        return ApiResponse.ok(platformGovernanceService.publishPlatformSkillVersion(companyId, id, request.versionNo(),
                new PlatformGovernanceService.SkillGovernanceCommand(
                        request.enabled(),
                        request.visibility(),
                        request.bindingPolicy()
                )));
    }

    @PostMapping("/skills/{id}/rollback")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformGovernanceService.PlatformSkillView> rollbackPlatformSkillVersion(
            @PathVariable Long id,
            @Valid @RequestBody PlatformSkillPublishRequest request) {
        String companyId = platformScopeId();
        return ApiResponse.ok(platformGovernanceService.rollbackPlatformSkillVersion(companyId, id, request.versionNo(),
                new PlatformGovernanceService.SkillGovernanceCommand(
                        request.enabled(),
                        request.visibility(),
                        request.bindingPolicy()
                )));
    }

    @GetMapping("/tools")
    public ApiResponse<List<PlatformGovernanceService.PlatformToolView>> listBuiltinTools() {
        String companyId = platformScopeId();
        return ApiResponse.ok(platformGovernanceService.listPlatformTools(companyId));
    }

    @PutMapping("/tools/{toolName}")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformGovernanceService.PlatformToolView> updateBuiltinTool(
            @PathVariable String toolName,
            @Valid @RequestBody PlatformToolUpdateRequest request) {
        String companyId = platformScopeId();
        return ApiResponse.ok(platformGovernanceService.updatePlatformTool(companyId, toolName,
                new PlatformGovernanceService.ToolGovernanceCommand(
                        request.displayName(),
                        request.description(),
                        request.riskLevel(),
                        request.category(),
                        request.enabled()
                )));
    }

    @GetMapping("/audit/logs")
    public ApiResponse<Map<String, Object>> listPlatformAuditLogs(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "eventType", required = false) String eventType,
            @RequestParam(name = "resourceType", required = false) String resourceType,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        String companyId = platformScopeId();
        return ApiResponse.ok(platformAuditService.query(companyId, new PlatformAuditService.PlatformAuditLogQuery(
                parseInstant(from),
                parseInstant(to),
                blankToNull(eventType),
                blankToNull(resourceType),
                blankToNull(q),
                limit
        )));
    }

    private String platformScopeId() {
        String configured = platformAccountProperties.getGovernanceCompanyId();
        return configured == null || configured.isBlank()
                ? PlatformAccountProperties.LEGACY_DEFAULT_GOVERNANCE_COMPANY_ID
                : configured.trim();
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid instant: " + value);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record PlatformSkillDraftRequest(
            @NotBlank String name,
            String description,
            String promptFragment,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule,
            String outputContract,
            @NotBlank String riskLevel,
            String changelog
    ) {
    }

    public record PlatformSkillPublishRequest(
            @NotNull Integer versionNo,
            Boolean enabled,
            String visibility,
            String bindingPolicy
    ) {
    }

    public record PolicyBundleDraftRequest(
            @NotBlank String name,
            String description,
            String promptFragment,
            List<String> handoffRules,
            List<String> sourceSkillCodes
    ) {
    }

    public record PolicyBundlePublishRequest(
            @NotNull Integer versionNo
    ) {
    }

    public record PlatformToolUpdateRequest(
            @NotBlank String displayName,
            String description,
            @NotBlank String riskLevel,
            @NotBlank String category,
            Boolean enabled
    ) {
    }
}
