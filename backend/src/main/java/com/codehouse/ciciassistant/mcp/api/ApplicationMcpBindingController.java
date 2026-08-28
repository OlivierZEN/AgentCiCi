package com.codehouse.ciciassistant.mcp.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.mcp.service.ApplicationMcpBindingService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tenant-applications/{appCode}/mcp-bindings")
@RequireOrgAdmin
public class ApplicationMcpBindingController {
    private final ApplicationMcpBindingService bindings;

    public ApplicationMcpBindingController(ApplicationMcpBindingService bindings) { this.bindings = bindings; }

    @GetMapping
    public ApiResponse<List<ApplicationMcpBindingService.BindingView>> list(@PathVariable String appCode) {
        return ApiResponse.ok(bindings.list(TenantContext.requireCompanyId(), appCode));
    }

    @PutMapping("/{providerKey}")
    public ApiResponse<ApplicationMcpBindingService.BindingView> bind(@PathVariable String appCode,
            @PathVariable String providerKey, @Valid @RequestBody BindRequest request) throws Exception {
        return ApiResponse.ok(bindings.bind(TenantContext.requireCompanyId(), appCode, request.version(),
                providerKey, request.serverId(), TenantContext.getUserId().orElse("platform")));
    }

    @PostMapping("/{providerKey}/disable")
    public ApiResponse<Void> disable(@PathVariable String appCode, @PathVariable String providerKey) {
        bindings.disable(TenantContext.requireCompanyId(), appCode, providerKey);
        return ApiResponse.ok(null);
    }

    public record BindRequest(
            @NotBlank @Pattern(regexp = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$") String version,
            @NotNull Long serverId) {}
}
