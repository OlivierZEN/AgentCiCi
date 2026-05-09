package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.platform.service.PlatformTenantLifecycleService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/tenants")
@RequirePlatformRole
public class PlatformTenantLifecycleController {

    private final PlatformTenantLifecycleService tenantLifecycleService;

    public PlatformTenantLifecycleController(PlatformTenantLifecycleService tenantLifecycleService) {
        this.tenantLifecycleService = tenantLifecycleService;
    }

    @GetMapping
    public ApiResponse<List<PlatformTenantLifecycleService.TenantLifecycleView>> listTenants() {
        return ApiResponse.ok(tenantLifecycleService.listTenants());
    }

    @GetMapping("/{orgId}/retention")
    public ApiResponse<PlatformTenantLifecycleService.TenantRetentionDetailView> getRetention(@PathVariable String orgId) {
        return ApiResponse.ok(tenantLifecycleService.getRetentionDetail(orgId));
    }

    @PatchMapping("/{orgId}/retention")
    public ApiResponse<PlatformTenantLifecycleService.TenantRetentionDetailView> updateRetention(
            @PathVariable String orgId,
            @Valid @RequestBody RetentionPolicyRequest request) {
        return ApiResponse.ok(tenantLifecycleService.updateRetention(orgId,
                new PlatformTenantLifecycleService.RetentionUpdateCommand(
                        request.graceUntil(),
                        request.suspendUntil(),
                        request.exportDeadline(),
                        request.purgeAfter(),
                        request.legalHold(),
                        request.policySource(),
                        request.legalHoldReason(),
                        request.legalHoldApprovedBy(),
                        request.legalHoldApprovedAt(),
                        request.legalHoldReviewAt()
                ),
                actorId(),
                actorRole()));
    }

    @PostMapping("/{orgId}/suspend")
    public ApiResponse<PlatformTenantLifecycleService.TenantLifecycleView> suspendTenant(
            @PathVariable String orgId,
            @RequestBody(required = false) LifecycleActionRequest request) {
        return ApiResponse.ok(tenantLifecycleService.suspendTenant(orgId, actorId(), actorRole(),
                request == null ? null : request.reason()));
    }

    @PostMapping("/{orgId}/resume")
    public ApiResponse<PlatformTenantLifecycleService.TenantLifecycleView> resumeTenant(
            @PathVariable String orgId,
            @RequestBody(required = false) LifecycleActionRequest request) {
        return ApiResponse.ok(tenantLifecycleService.resumeTenant(orgId, actorId(), actorRole(),
                request == null ? null : request.reason()));
    }

    @PostMapping("/{orgId}/pending-purge")
    public ApiResponse<PlatformTenantLifecycleService.TenantLifecycleView> markPendingPurge(
            @PathVariable String orgId,
            @RequestBody(required = false) LifecycleActionRequest request) {
        return ApiResponse.ok(tenantLifecycleService.markPendingPurge(orgId, actorId(), actorRole(),
                request == null ? null : request.reason()));
    }

    @PostMapping("/{orgId}/purge-jobs")
    public ApiResponse<PlatformTenantLifecycleService.PurgeJobView> createPurgeJob(
            @PathVariable String orgId,
            @Valid @RequestBody PurgeJobRequest request) {
        return ApiResponse.ok(tenantLifecycleService.createPurgeJob(orgId,
                new PlatformTenantLifecycleService.PurgeJobCreateCommand(
                        request.dryRun(),
                        request.reason(),
                        request.sourceDryRunJobId(),
                        request.confirmationText()
                ),
                actorId(),
                actorRole()));
    }

    @GetMapping("/{orgId}/purge-jobs/{jobId}")
    public ApiResponse<PlatformTenantLifecycleService.PurgeJobView> getPurgeJob(
            @PathVariable String orgId,
            @PathVariable Long jobId) {
        return ApiResponse.ok(tenantLifecycleService.getPurgeJob(orgId, jobId));
    }

    @PostMapping("/{orgId}/purge-jobs/{jobId}/retry")
    public ApiResponse<PlatformTenantLifecycleService.PurgeJobView> retryPurgeJob(
            @PathVariable String orgId,
            @PathVariable Long jobId,
            @RequestBody(required = false) PurgeJobRetryRequest request) {
        return ApiResponse.ok(tenantLifecycleService.retryPurgeJob(orgId,
                jobId,
                new PlatformTenantLifecycleService.PurgeJobRetryCommand(
                        request == null ? null : request.confirmationText(),
                        request == null ? null : request.reason()
                ),
                actorId(),
                actorRole()));
    }

    @PostMapping("/{orgId}/purge-jobs/{jobId}/cancel")
    public ApiResponse<PlatformTenantLifecycleService.PurgeJobView> cancelPurgeJob(
            @PathVariable String orgId,
            @PathVariable Long jobId,
            @RequestBody(required = false) LifecycleActionRequest request) {
        return ApiResponse.ok(tenantLifecycleService.cancelPurgeJob(orgId,
                jobId,
                actorId(),
                actorRole(),
                request == null ? null : request.reason()));
    }

    @PostMapping("/{orgId}/export-jobs")
    public ApiResponse<PlatformTenantLifecycleService.ExportJobView> createExportJob(
            @PathVariable String orgId,
            @RequestBody(required = false) ExportJobRequest request) {
        return ApiResponse.ok(tenantLifecycleService.createExportJob(orgId, actorId(), actorRole(),
                request == null ? null : request.reason()));
    }

    @GetMapping("/{orgId}/export-jobs")
    public ApiResponse<List<PlatformTenantLifecycleService.ExportJobView>> listExportJobs(@PathVariable String orgId) {
        return ApiResponse.ok(tenantLifecycleService.listExportJobs(orgId, true));
    }

    @GetMapping("/{orgId}/export-jobs/{jobId}")
    public ApiResponse<PlatformTenantLifecycleService.ExportJobView> getExportJob(
            @PathVariable String orgId,
            @PathVariable Long jobId) {
        return ApiResponse.ok(tenantLifecycleService.getExportJob(orgId, jobId, true));
    }

    @GetMapping("/{orgId}/export-jobs/{jobId}/download")
    public ResponseEntity<ApiResponse<Void>> rejectPlatformExportDownload(@PathVariable String orgId,
                                                                          @PathVariable Long jobId) {
        return ResponseEntity.status(403)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.fail("Platform operators can view export metadata but cannot download business-content archives"));
    }

    private String actorId() {
        return TenantContext.getUserId().orElse("platform");
    }

    private String actorRole() {
        return TenantContext.getRoles().stream()
                .filter(role -> role.startsWith("PLATFORM_"))
                .findFirst()
                .orElse("PLATFORM");
    }

    public record RetentionPolicyRequest(
            String graceUntil,
            String suspendUntil,
            String exportDeadline,
            String purgeAfter,
            Boolean legalHold,
            String policySource,
            String legalHoldReason,
            String legalHoldApprovedBy,
            String legalHoldApprovedAt,
            String legalHoldReviewAt
    ) {
    }

    public record LifecycleActionRequest(String reason) {
    }

    public record PurgeJobRequest(Boolean dryRun, String reason, Long sourceDryRunJobId, String confirmationText) {
    }

    public record PurgeJobRetryRequest(String confirmationText, String reason) {
    }

    public record ExportJobRequest(String reason) {
    }
}
