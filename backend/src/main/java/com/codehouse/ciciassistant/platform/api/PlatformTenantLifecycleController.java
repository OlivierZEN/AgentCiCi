package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.auth.RequirePlatformRole;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.platform.service.PlatformTenantLifecycleService;
import com.codehouse.ciciassistant.platform.service.DevAutopilotTenantApplicationService;
import com.codehouse.ciciassistant.semattice.SematticeProvisioningClient;
import com.codehouse.ciciassistant.semattice.SematticeProvisioningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    private final SematticeProvisioningClient sematticeProvisioningClient;
    private final SematticeProvisioningService sematticeProvisioningService;
    private final DevAutopilotTenantApplicationService devAutopilotApplications;

    public PlatformTenantLifecycleController(PlatformTenantLifecycleService tenantLifecycleService,
                                             SematticeProvisioningClient sematticeProvisioningClient,
                                             SematticeProvisioningService sematticeProvisioningService,
                                             DevAutopilotTenantApplicationService devAutopilotApplications) {
        this.tenantLifecycleService = tenantLifecycleService;
        this.sematticeProvisioningClient = sematticeProvisioningClient;
        this.sematticeProvisioningService = sematticeProvisioningService;
        this.devAutopilotApplications = devAutopilotApplications;
    }

    @GetMapping
    public ApiResponse<List<PlatformTenantLifecycleService.TenantLifecycleView>> listTenants() {
        return ApiResponse.ok(tenantLifecycleService.listTenants());
    }

    @PostMapping
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformTenantLifecycleService.TenantProvisionView> createTenant(
            @Valid @RequestBody CreateTenantRequest request) {
        return ApiResponse.ok(tenantLifecycleService.createTenant(
                new PlatformTenantLifecycleService.TenantProvisionCommand(
                        request.tenantName(),
                        request.ownerMobile(),
                        request.ownerDisplayName(),
                        request.ownerEmail(),
                        request.initialPassword(),
                        request.provisionNote()
                ),
                actorId(),
                actorRole()));
    }

    @PostMapping("/{companyId}/semattice-provisionings")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<SematticeProvisioningClient.ProvisioningView> provisionSemattice(
            @PathVariable @Pattern(regexp = "^org[a-z0-9]{17}$") String companyId,
            @Valid @RequestBody SematticeProvisioningRequest request) {
        // Idempotency: if already provisioned, return existing result instead of re-calling Semattice (which would 409/502)
        SematticeProvisioningService.BindingView existing = sematticeProvisioningService.getProvisioningStatus(companyId);
        if ("PROVISIONED".equals(existing.state()) && existing.sematticeTenantId() != null) {
            return ApiResponse.ok(new SematticeProvisioningClient.ProvisioningView(
                    existing.companyId(), existing.sematticeTenantId(), "active", "succeeded"), "已开通");
        }
        return ApiResponse.ok(sematticeProvisioningClient.provision(companyId, request.idempotencyKey(), request.displayName(),
                request.serviceTier(), request.entitlements()));
    }

    @GetMapping("/{companyId}/semattice-provisionings")
    public ApiResponse<SematticeProvisioningService.BindingView> getSematticeProvisioningStatus(
            @PathVariable @Pattern(regexp = "^org[a-z0-9]{17}$") String companyId) {
        return ApiResponse.ok(sematticeProvisioningService.getProvisioningStatus(companyId));
    }

    @GetMapping("/{companyId}/applications/devautopilot")
    public ApiResponse<DevAutopilotTenantApplicationService.View> getDevAutopilotApplication(
            @PathVariable @Pattern(regexp = "^org[a-z0-9]{17}$") String companyId) {
        return ApiResponse.ok(devAutopilotApplications.get(companyId));
    }

    @PostMapping("/{companyId}/applications/devautopilot/activations")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<DevAutopilotTenantApplicationService.View> activateDevAutopilot(
            @PathVariable @Pattern(regexp = "^org[a-z0-9]{17}$") String companyId,
            @Valid @RequestBody DevAutopilotActivationRequest request) {
        return ApiResponse.ok(devAutopilotApplications.activate(companyId,
                new DevAutopilotTenantApplicationService.ActivationCommand(request.idempotencyKey(), request.productManagerName(),
                        request.productManagerAlias(), request.ownerMemberId()), actorId()));
    }

    @PostMapping("/{companyId}/applications/devautopilot/suspensions")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<DevAutopilotTenantApplicationService.View> suspendDevAutopilot(
            @PathVariable @Pattern(regexp = "^org[a-z0-9]{17}$") String companyId) {
        return ApiResponse.ok(devAutopilotApplications.suspend(companyId, actorId()));
    }

    @PostMapping("/{companyId}/applications/devautopilot/resumptions")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<DevAutopilotTenantApplicationService.View> resumeDevAutopilot(
            @PathVariable @Pattern(regexp = "^org[a-z0-9]{17}$") String companyId) {
        return ApiResponse.ok(devAutopilotApplications.resume(companyId, actorId()));
    }

    @PostMapping("/{companyId}/applications/devautopilot/developer-principals")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<DevAutopilotTenantApplicationService.ResourceView> addDevAutopilotDeveloper(
            @PathVariable @Pattern(regexp = "^org[a-z0-9]{17}$") String companyId,
            @Valid @RequestBody DevAutopilotDeveloperRequest request) {
        return ApiResponse.ok(devAutopilotApplications.addDeveloper(companyId,
                new DevAutopilotTenantApplicationService.DeveloperCommand(request.displayName(), request.resourceAlias(), request.ownerMemberId()), actorId()));
    }

    @GetMapping("/{companyId}/retention")
    public ApiResponse<PlatformTenantLifecycleService.TenantRetentionDetailView> getRetention(@PathVariable String companyId) {
        return ApiResponse.ok(tenantLifecycleService.getRetentionDetail(companyId));
    }

    @PatchMapping("/{companyId}/retention")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformTenantLifecycleService.TenantRetentionDetailView> updateRetention(
            @PathVariable String companyId,
            @Valid @RequestBody RetentionPolicyRequest request) {
        return ApiResponse.ok(tenantLifecycleService.updateRetention(companyId,
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

    @PostMapping("/{companyId}/suspend")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformTenantLifecycleService.TenantLifecycleView> suspendTenant(
            @PathVariable String companyId,
            @RequestBody(required = false) LifecycleActionRequest request) {
        return ApiResponse.ok(tenantLifecycleService.suspendTenant(companyId, actorId(), actorRole(),
                request == null ? null : request.reason()));
    }

    @PostMapping("/{companyId}/resume")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformTenantLifecycleService.TenantLifecycleView> resumeTenant(
            @PathVariable String companyId,
            @RequestBody(required = false) LifecycleActionRequest request) {
        return ApiResponse.ok(tenantLifecycleService.resumeTenant(companyId, actorId(), actorRole(),
                request == null ? null : request.reason()));
    }

    @PostMapping("/{companyId}/pending-purge")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformTenantLifecycleService.TenantLifecycleView> markPendingPurge(
            @PathVariable String companyId,
            @RequestBody(required = false) LifecycleActionRequest request) {
        return ApiResponse.ok(tenantLifecycleService.markPendingPurge(companyId, actorId(), actorRole(),
                request == null ? null : request.reason()));
    }

    @PostMapping("/{companyId}/purge-jobs")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformTenantLifecycleService.PurgeJobView> createPurgeJob(
            @PathVariable String companyId,
            @Valid @RequestBody PurgeJobRequest request) {
        return ApiResponse.ok(tenantLifecycleService.createPurgeJob(companyId,
                new PlatformTenantLifecycleService.PurgeJobCreateCommand(
                        request.dryRun(),
                        request.reason(),
                        request.sourceDryRunJobId(),
                        request.confirmationText()
                ),
                actorId(),
                actorRole()));
    }

    @GetMapping("/{companyId}/purge-jobs/{jobId}")
    public ApiResponse<PlatformTenantLifecycleService.PurgeJobView> getPurgeJob(
            @PathVariable String companyId,
            @PathVariable Long jobId) {
        return ApiResponse.ok(tenantLifecycleService.getPurgeJob(companyId, jobId));
    }

    @PostMapping("/{companyId}/purge-jobs/{jobId}/retry")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformTenantLifecycleService.PurgeJobView> retryPurgeJob(
            @PathVariable String companyId,
            @PathVariable Long jobId,
            @RequestBody(required = false) PurgeJobRetryRequest request) {
        return ApiResponse.ok(tenantLifecycleService.retryPurgeJob(companyId,
                jobId,
                new PlatformTenantLifecycleService.PurgeJobRetryCommand(
                        request == null ? null : request.confirmationText(),
                        request == null ? null : request.reason()
                ),
                actorId(),
                actorRole()));
    }

    @PostMapping("/{companyId}/purge-jobs/{jobId}/cancel")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformTenantLifecycleService.PurgeJobView> cancelPurgeJob(
            @PathVariable String companyId,
            @PathVariable Long jobId,
            @RequestBody(required = false) LifecycleActionRequest request) {
        return ApiResponse.ok(tenantLifecycleService.cancelPurgeJob(companyId,
                jobId,
                actorId(),
                actorRole(),
                request == null ? null : request.reason()));
    }

    @PostMapping("/{companyId}/export-jobs")
    @RequirePlatformRole({RoleCodes.PLATFORM_ADMIN, RoleCodes.PLATFORM_OPERATOR})
    public ApiResponse<PlatformTenantLifecycleService.ExportJobView> createExportJob(
            @PathVariable String companyId,
            @RequestBody(required = false) ExportJobRequest request) {
        return ApiResponse.ok(tenantLifecycleService.createExportJob(companyId, actorId(), actorRole(),
                request == null ? null : request.reason()));
    }

    @GetMapping("/{companyId}/export-jobs")
    public ApiResponse<List<PlatformTenantLifecycleService.ExportJobView>> listExportJobs(@PathVariable String companyId) {
        return ApiResponse.ok(tenantLifecycleService.listExportJobs(companyId, true));
    }

    @GetMapping("/{companyId}/export-jobs/{jobId}")
    public ApiResponse<PlatformTenantLifecycleService.ExportJobView> getExportJob(
            @PathVariable String companyId,
            @PathVariable Long jobId) {
        return ApiResponse.ok(tenantLifecycleService.getExportJob(companyId, jobId, true));
    }

    @GetMapping("/{companyId}/export-jobs/{jobId}/download")
    public ResponseEntity<ApiResponse<Void>> rejectPlatformExportDownload(@PathVariable String companyId,
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

    public record CreateTenantRequest(
            @NotBlank String tenantName,
            @NotBlank
            @Pattern(regexp = "^1\\d{10}$", message = "must be an 11-digit mainland China mobile number")
            String ownerMobile,
            String ownerDisplayName,
            String ownerEmail,
            String initialPassword,
            String provisionNote
    ) {
    }

    public record SematticeProvisioningRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$") String idempotencyKey,
            @NotBlank @Size(max = 256) String displayName,
            @NotBlank @Size(max = 64) String serviceTier,
            JsonNode entitlements
    ) {
        @AssertTrue(message = "entitlements must be a JSON object")
        public boolean hasValidEntitlements() {
            return entitlements == null || entitlements.isObject();
        }
    }

    public record DevAutopilotActivationRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$") String idempotencyKey,
            @NotBlank @Size(max = 128) String productManagerName,
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{1,47}$") String productManagerAlias,
            @NotBlank String ownerMemberId) { }

    public record DevAutopilotDeveloperRequest(
            @NotBlank @Size(max = 128) String displayName,
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{1,47}$") String resourceAlias,
            @NotBlank String ownerMemberId) { }

    public record LifecycleActionRequest(String reason) {
    }

    public record PurgeJobRequest(Boolean dryRun, String reason, Long sourceDryRunJobId, String confirmationText) {
    }

    public record PurgeJobRetryRequest(String confirmationText, String reason) {
    }

    public record ExportJobRequest(String reason) {
    }
}
