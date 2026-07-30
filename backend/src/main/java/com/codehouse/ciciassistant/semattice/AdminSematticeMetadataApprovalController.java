package com.codehouse.ciciassistant.semattice;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Organization-admin control plane for independently approving Semattice metadata changes. */
@RestController
@RequireOrgAdmin
@RequestMapping("/admin/semattice/metadata-approvals")
public class AdminSematticeMetadataApprovalController {

    private final SematticeMetadataApprovalService service;

    public AdminSematticeMetadataApprovalController(SematticeMetadataApprovalService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<SematticeMetadataApprovalService.ApprovalView>> list() {
        return ApiResponse.ok(service.list(TenantContext.requireCompanyId()));
    }

    @PostMapping
    public ApiResponse<SematticeMetadataApprovalService.ApprovalView> request(@Valid @RequestBody RequestApproval body) {
        return ApiResponse.ok(service.request(TenantContext.requireCompanyId(), currentMemberId(),
                body.subjectType(), body.subjectId(), body.summary()), "审批请求已创建");
    }

    @PostMapping("/{approvalId}/approve")
    public ApiResponse<SematticeMetadataApprovalService.ApprovalView> approve(@PathVariable String approvalId) {
        return ApiResponse.ok(service.approve(TenantContext.requireCompanyId(), currentMemberId(), approvalId), "审批已通过");
    }

    private String currentMemberId() {
        return TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
    }

    public record RequestApproval(@NotBlank String subjectType, @NotBlank String subjectId, @NotBlank String summary) {
    }
}
