package com.codehouse.ciciassistant.platform.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.platform.service.PlatformTenantLifecycleService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireOrgAdmin
@RequestMapping("/admin/company")
public class AdminCompanyLifecycleController {

    private final PlatformTenantLifecycleService tenantLifecycleService;

    public AdminCompanyLifecycleController(PlatformTenantLifecycleService tenantLifecycleService) {
        this.tenantLifecycleService = tenantLifecycleService;
    }

    @PostMapping("/export-jobs")
    public ApiResponse<PlatformTenantLifecycleService.ExportJobView> createExportJob(
            @RequestBody(required = false) ExportJobRequest request) {
        String companyId = TenantContext.requireCompanyId();
        String actorId = TenantContext.getUserId().orElse("org-admin");
        String actorRole = TenantContext.getRoles().stream().findFirst().orElse("ORG_ADMIN");
        return ApiResponse.ok(tenantLifecycleService.createExportJob(companyId, actorId, actorRole,
                request == null ? null : request.reason()));
    }

    @GetMapping("/export-jobs")
    public ApiResponse<List<PlatformTenantLifecycleService.ExportJobView>> listExportJobs() {
        return ApiResponse.ok(tenantLifecycleService.listExportJobs(TenantContext.requireCompanyId(), true));
    }

    @GetMapping("/export-jobs/{jobId}")
    public ApiResponse<PlatformTenantLifecycleService.ExportJobView> getExportJob(@PathVariable Long jobId) {
        return ApiResponse.ok(tenantLifecycleService.getExportJob(TenantContext.requireCompanyId(), jobId, true));
    }

    @GetMapping("/export-jobs/{jobId}/download")
    public ResponseEntity<byte[]> downloadExport(@PathVariable Long jobId) {
        PlatformTenantLifecycleService.ExportArtifact artifact = tenantLifecycleService.downloadExport(
                TenantContext.requireCompanyId(),
                jobId
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(artifact.filename())
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(artifact.bytes());
    }

    public record ExportJobRequest(String reason) {
    }
}
