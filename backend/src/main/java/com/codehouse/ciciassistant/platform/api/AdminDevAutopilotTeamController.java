package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.platform.service.DevAutopilotTenantApplicationService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant-side team management. Company and actor come from the authenticated session; owner is tenant-local. */
@RestController
@RequestMapping("/admin/devautopilot/team")
@RequireOrgAdmin
public class AdminDevAutopilotTeamController {

    private final DevAutopilotTenantApplicationService applications;

    public AdminDevAutopilotTeamController(DevAutopilotTenantApplicationService applications) {
        this.applications = applications;
    }

    @GetMapping
    public ApiResponse<DevAutopilotTenantApplicationService.View> get() {
        return ApiResponse.ok(applications.get(companyId()));
    }

    @PostMapping("/product-managers")
    public ApiResponse<DevAutopilotTenantApplicationService.TeamResourceView> createProductManager(
            @Valid @RequestBody CreateTeamMemberRequest request) {
        return ApiResponse.ok(applications.createProductManager(companyId(), request.displayName(), actorMemberId(), request.ownerMemberId()));
    }

    @PostMapping("/developers")
    public ApiResponse<DevAutopilotTenantApplicationService.TeamResourceView> createDeveloper(
            @Valid @RequestBody CreateTeamMemberRequest request) {
        return ApiResponse.ok(applications.addDeveloper(companyId(), request.displayName(), actorMemberId(),
                request.ownerMemberId(), request.maxInstances() == null ? 1 : request.maxInstances()));
    }

    @PutMapping("/developers/{principalId}/runtime-policy")
    public ApiResponse<DevAutopilotTenantApplicationService.ResourceView> updateDeveloperRuntimePolicy(
            @org.springframework.web.bind.annotation.PathVariable String principalId,
            @Valid @RequestBody UpdateDeveloperRuntimePolicyRequest request) {
        return ApiResponse.ok(applications.updateDeveloperRuntimePolicy(companyId(), principalId,
                request.maxInstances(), request.expectedRevision(), actorMemberId()));
    }

    @GetMapping("/access-members")
    public ApiResponse<List<DevAutopilotTenantApplicationService.ApplicationMemberAccessView>> accessMembers() {
        return ApiResponse.ok(applications.listAccessMembers(companyId()));
    }

    @PutMapping("/access-members")
    public ApiResponse<List<DevAutopilotTenantApplicationService.ApplicationMemberAccessView>> replaceAccessMembers(
            @Valid @RequestBody ReplaceAccessMembersRequest request) {
        return ApiResponse.ok(applications.replaceAccessMembers(companyId(), actorMemberId(), request.members()));
    }

    private String companyId() {
        return TenantContext.requireCompanyId();
    }

    private String actorMemberId() {
        return TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
    }

    public record CreateTeamMemberRequest(
            @NotBlank @Size(max = 128) String displayName,
            @NotBlank String ownerMemberId,
            @Min(1) @Max(64) Integer maxInstances) {
    }

    public record UpdateDeveloperRuntimePolicyRequest(
            @Min(1) @Max(64) int maxInstances,
            @Min(1) long expectedRevision) {
    }

    public record ReplaceAccessMembersRequest(
            List<DevAutopilotTenantApplicationService.ApplicationMemberRoleInput> members) {
    }
}
