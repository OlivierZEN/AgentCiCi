package com.codehouse.ciciassistant.organization;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireOrgAdmin
@RequestMapping("/admin/organization/profile")
public class AdminOrganizationProfileController {

    private final AdminOrganizationProfileService service;

    public AdminOrganizationProfileController(AdminOrganizationProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<AdminOrganizationProfileService.OrganizationProfileView> getProfile() {
        return ApiResponse.ok(service.getProfile(TenantContext.requireOrgId()));
    }

    @PatchMapping
    public ApiResponse<AdminOrganizationProfileService.OrganizationProfileView> updateProfile(
            @RequestBody AdminOrganizationProfileService.ProfileUpdateCommand request) {
        String actorId = TenantContext.getUserId().orElse("org-admin");
        return ApiResponse.ok(
                service.updateProfile(TenantContext.requireOrgId(), actorId, request),
                "Organization profile updated");
    }
}
