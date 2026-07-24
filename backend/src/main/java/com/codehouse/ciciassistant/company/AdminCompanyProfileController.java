package com.codehouse.ciciassistant.company;

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
@RequestMapping("/admin/company/profile")
public class AdminCompanyProfileController {

    private final AdminCompanyProfileService service;

    public AdminCompanyProfileController(AdminCompanyProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<AdminCompanyProfileService.CompanyProfileView> getProfile() {
        return ApiResponse.ok(service.getProfile(TenantContext.requireCompanyId()));
    }

    @PatchMapping
    public ApiResponse<AdminCompanyProfileService.CompanyProfileView> updateProfile(
            @RequestBody AdminCompanyProfileService.ProfileUpdateCommand request) {
        String actorId = TenantContext.getUserId().orElse("org-admin");
        return ApiResponse.ok(
                service.updateProfile(TenantContext.requireCompanyId(), actorId, request),
                "Company profile updated");
    }
}
