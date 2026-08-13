package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.semattice.DevAutopilotIntakeReconciliationService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant-governed repair for historical DevAutopilot intake records. */
@RestController
@RequestMapping("/admin/devautopilot/intake-reconciliations")
@RequireOrgAdmin
public class AdminDevAutopilotIntakeController {

    private final DevAutopilotIntakeReconciliationService reconciliations;

    public AdminDevAutopilotIntakeController(DevAutopilotIntakeReconciliationService reconciliations) {
        this.reconciliations = reconciliations;
    }

    @PostMapping
    public ApiResponse<DevAutopilotIntakeReconciliationService.ReconciliationView> reconcile(
            @Valid @RequestBody ReconciliationRequest request) {
        return ApiResponse.ok(reconciliations.reconcile(
                TenantContext.requireCompanyId(),
                TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context")),
                request.sessionId(),
                request.recordId()));
    }

    public record ReconciliationRequest(
            @NotBlank @Size(max = 64) String sessionId,
            @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String recordId) {
    }
}
