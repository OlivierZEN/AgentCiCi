package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.platform.domain.PlatformAuditLogEntity;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.codehouse.ciciassistant.platform.service.PlatformGovernanceService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
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
@RequestMapping("/platform")
@RequirePlatformRole
public class PlatformController {

    private final PlatformGovernanceService platformGovernanceService;
    private final PlatformAuditService platformAuditService;

    public PlatformController(PlatformGovernanceService platformGovernanceService,
                              PlatformAuditService platformAuditService) {
        this.platformGovernanceService = platformGovernanceService;
        this.platformAuditService = platformAuditService;
    }

    @GetMapping("/bootstrap")
    public ApiResponse<Map<String, Object>> bootstrap() {
        String orgId = TenantContext.requireOrgId();
        platformGovernanceService.ensurePlatformAssets(orgId);
        List<PlatformGovernanceService.PlatformSkillView> platformSkills = platformGovernanceService.listPlatformSkills(orgId);
        List<PlatformGovernanceService.PlatformToolView> platformTools = platformGovernanceService.listPlatformTools(orgId);
        PlatformGovernanceService.PlatformPolicyBundleView corePolicyBundle =
                platformGovernanceService.getCorePolicyBundleSummary(orgId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orgId", orgId);
        payload.put("roles", TenantContext.getRoles());
        payload.put("skillCount", platformSkills.size());
        payload.put("hiddenSkillCount", platformSkills.stream().filter(item -> "HIDDEN".equals(item.visibility())).count());
        payload.put("builtinToolCount", platformTools.size());
        payload.put("recentAuditCount", platformAuditService.latest(orgId).size());
        payload.put("policyBundleCode", corePolicyBundle.bundleCode());
        payload.put("policyBundleVersionNo", corePolicyBundle.versionNo());
        payload.put("policyBundleLivePublishedAgentCount", corePolicyBundle.livePublishedAgentCount());
        return ApiResponse.ok(payload);
    }

    @GetMapping("/skills")
    public ApiResponse<List<PlatformGovernanceService.PlatformSkillView>> listPlatformSkills() {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(platformGovernanceService.listPlatformSkills(orgId));
    }

    @GetMapping("/skills/{id}/versions")
    public ApiResponse<List<PlatformGovernanceService.PlatformSkillVersionView>> listPlatformSkillVersions(
            @PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(platformGovernanceService.listPlatformSkillVersions(orgId, id));
    }

    @GetMapping("/policies/core")
    public ApiResponse<PlatformGovernanceService.PlatformPolicyBundleView> getCorePolicyBundle() {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(platformGovernanceService.getCorePolicyBundleSummary(orgId));
    }

    @GetMapping("/policies/core/versions")
    public ApiResponse<List<PlatformGovernanceService.PlatformPolicyBundleVersionView>> listCorePolicyBundleVersions() {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(platformGovernanceService.listCorePolicyBundleVersions(orgId));
    }

    @PostMapping("/policies/core/versions")
    public ApiResponse<PlatformGovernanceService.PlatformPolicyBundleView> createCorePolicyBundleDraft(
            @Valid @RequestBody PolicyBundleDraftRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(platformGovernanceService.saveCorePolicyBundleDraft(orgId,
                new PlatformGovernanceService.PolicyBundleDraftCommand(
                        request.name(),
                        request.description(),
                        request.promptFragment(),
                        request.handoffRules(),
                        request.sourceSkillCodes()
                )));
    }

    @PostMapping("/policies/core/publish")
    public ApiResponse<PlatformGovernanceService.PlatformPolicyBundleView> publishCorePolicyBundle(
            @Valid @RequestBody PolicyBundlePublishRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(platformGovernanceService.publishCorePolicyBundleVersion(orgId, request.versionNo()));
    }

    @PostMapping("/policies/core/rollback")
    public ApiResponse<PlatformGovernanceService.PlatformPolicyBundleView> rollbackCorePolicyBundle(
            @Valid @RequestBody PolicyBundlePublishRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(platformGovernanceService.rollbackCorePolicyBundleVersion(orgId, request.versionNo()));
    }

    @PostMapping("/skills/{id}/versions")
    public ApiResponse<PlatformGovernanceService.PlatformSkillView> createPlatformSkillDraft(
            @PathVariable Long id,
            @Valid @RequestBody PlatformSkillDraftRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(platformGovernanceService.savePlatformSkillDraft(orgId, id,
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
    public ApiResponse<PlatformGovernanceService.PlatformSkillView> publishPlatformSkillVersion(
            @PathVariable Long id,
            @Valid @RequestBody PlatformSkillPublishRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(platformGovernanceService.publishPlatformSkillVersion(orgId, id, request.versionNo(),
                new PlatformGovernanceService.SkillGovernanceCommand(
                        request.enabled(),
                        request.visibility(),
                        request.bindingPolicy()
                )));
    }

    @PostMapping("/skills/{id}/rollback")
    public ApiResponse<PlatformGovernanceService.PlatformSkillView> rollbackPlatformSkillVersion(
            @PathVariable Long id,
            @Valid @RequestBody PlatformSkillPublishRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(platformGovernanceService.rollbackPlatformSkillVersion(orgId, id, request.versionNo(),
                new PlatformGovernanceService.SkillGovernanceCommand(
                        request.enabled(),
                        request.visibility(),
                        request.bindingPolicy()
                )));
    }

    @GetMapping("/tools")
    public ApiResponse<List<PlatformGovernanceService.PlatformToolView>> listBuiltinTools() {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(platformGovernanceService.listPlatformTools(orgId));
    }

    @PutMapping("/tools/{toolName}")
    public ApiResponse<PlatformGovernanceService.PlatformToolView> updateBuiltinTool(
            @PathVariable String toolName,
            @Valid @RequestBody PlatformToolUpdateRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(platformGovernanceService.updatePlatformTool(orgId, toolName,
                new PlatformGovernanceService.ToolGovernanceCommand(
                        request.displayName(),
                        request.description(),
                        request.riskLevel(),
                        request.category(),
                        request.enabled()
                )));
    }

    @GetMapping("/audit/logs")
    public ApiResponse<List<Map<String, Object>>> listPlatformAuditLogs() {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(platformAuditService.latest(orgId).stream().map(this::toAuditPayload).toList());
    }

    private Map<String, Object> toAuditPayload(PlatformAuditLogEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("orgId", item.getOrgId());
        payload.put("userId", item.getUserId());
        payload.put("roleCode", item.getRoleCode());
        payload.put("eventType", item.getEventType());
        payload.put("resourceType", item.getResourceType());
        payload.put("resourceKey", item.getResourceKey());
        payload.put("detail", item.getDetail());
        payload.put("createdAt", item.getCreatedAt().toString());
        return payload;
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
