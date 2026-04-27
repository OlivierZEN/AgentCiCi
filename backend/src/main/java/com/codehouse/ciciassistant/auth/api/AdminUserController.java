package com.codehouse.ciciassistant.auth.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.auth.service.AdminUserService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@RequireOrgAdmin
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(adminUserService.listUsers(orgId));
    }

    @PutMapping("/{userId}/role")
    public ApiResponse<Map<String, Object>> updateRole(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        String orgId = TenantContext.requireOrgId();
        String actorId = TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
        return ApiResponse.ok(adminUserService.updateRole(orgId, actorId, userId, request.roleCode()));
    }

    @PutMapping("/{userId}/profile")
    public ApiResponse<Map<String, Object>> updateProfile(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        String orgId = TenantContext.requireOrgId();
        return ApiResponse.ok(adminUserService.updateProfile(
                orgId,
                userId,
                request.mobile(),
                request.nickname(),
                request.ccUsername(),
                request.ccSafetymark(),
                request.avatarBase64()));
    }

    public record UpdateUserRoleRequest(@NotBlank String roleCode) {
    }

    public record UpdateUserProfileRequest(
            String mobile,
            String nickname,
            String ccUsername,
            String ccSafetymark,
            String avatarBase64
    ) {
    }
}
