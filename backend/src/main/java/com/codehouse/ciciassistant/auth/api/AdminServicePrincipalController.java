package com.codehouse.ciciassistant.auth.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.auth.service.ServicePrincipalService;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
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

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateServicePrincipalRequest request) {
        return ApiResponse.ok(service.create(
                TenantContext.requireCompanyId(),
                TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context")),
                request.displayName(), request.serviceKind(), request.audience(), request.clientId()));
    }

    public record CreateServicePrincipalRequest(
            @NotBlank String displayName,
            @NotBlank String serviceKind,
            @NotBlank String audience,
            String clientId) {
    }
}
