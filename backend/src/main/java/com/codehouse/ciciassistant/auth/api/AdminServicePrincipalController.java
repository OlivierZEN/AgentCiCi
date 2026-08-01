package com.codehouse.ciciassistant.auth.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.auth.service.ServicePrincipalService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/service-principals")
@RequireOrgAdmin
public class AdminServicePrincipalController {

    private final ServicePrincipalService service;

    public AdminServicePrincipalController(ServicePrincipalService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.list(TenantContext.requireCompanyId()));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateServicePrincipalRequest request) {
        return ApiResponse.ok(service.create(
                TenantContext.requireCompanyId(),
                TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context")),
                request.displayName(), request.serviceKind(), request.audience(), request.clientId(), request.scopes()));
    }

    @PostMapping("/{principalId}/rotate-secret")
    public ApiResponse<Map<String, Object>> rotateSecret(@PathVariable String principalId) {
        return ApiResponse.ok(service.rotateSecret(companyId(), actorMemberId(), principalId));
    }

    @PostMapping("/{principalId}/suspend")
    public ApiResponse<Map<String, Object>> suspend(@PathVariable String principalId) {
        return ApiResponse.ok(service.suspend(companyId(), actorMemberId(), principalId));
    }

    @PostMapping("/{principalId}/activate")
    public ApiResponse<Map<String, Object>> activate(@PathVariable String principalId) {
        return ApiResponse.ok(service.activate(companyId(), actorMemberId(), principalId));
    }

    @PostMapping("/{principalId}/revoke")
    public ApiResponse<Map<String, Object>> revoke(@PathVariable String principalId) {
        return ApiResponse.ok(service.revoke(companyId(), actorMemberId(), principalId));
    }

    @PostMapping("/{principalId}/transfer-owner")
    public ApiResponse<Map<String, Object>> transferOwner(@PathVariable String principalId,
                                                          @Valid @RequestBody TransferOwnerRequest request) {
        return ApiResponse.ok(service.transferOwner(companyId(), actorMemberId(), principalId, request.ownerMemberId()));
    }

    private String companyId() {
        return TenantContext.requireCompanyId();
    }

    private String actorMemberId() {
        return TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
    }

    public record CreateServicePrincipalRequest(
            @NotBlank String displayName,
            @NotBlank String serviceKind,
            @NotBlank String audience,
            String clientId,
            List<@NotBlank String> scopes) {
    }

    public record TransferOwnerRequest(@NotBlank String ownerMemberId) {
    }
}
