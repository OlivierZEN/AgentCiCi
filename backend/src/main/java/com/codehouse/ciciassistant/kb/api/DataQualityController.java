package com.codehouse.ciciassistant.kb.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.kb.service.KbDataQualityService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/data-quality")
@RequireOrgAdmin
public class DataQualityController {

    private final KbDataQualityService kbDataQualityService;

    public DataQualityController(KbDataQualityService kbDataQualityService) {
        this.kbDataQualityService = kbDataQualityService;
    }

    @GetMapping("/sources")
    public ApiResponse<List<Map<String, Object>>> listSources() {
        return ApiResponse.ok(kbDataQualityService.listSources(TenantContext.requireOrgId()));
    }

    @PostMapping("/knowledge-bases/{kbId}/runs")
    public ApiResponse<Map<String, Object>> startScan(@PathVariable Long kbId,
                                                       @RequestBody(required = false) KnowledgeBaseController.QualityScanRequest request) {
        return ApiResponse.ok(kbDataQualityService.startScan(
                TenantContext.requireOrgId(),
                kbId,
                currentUserId(),
                new KbDataQualityService.QualityScanCommand(request == null ? "MANUAL" : request.triggerType())));
    }

    @GetMapping("/knowledge-bases/{kbId}/runs")
    public ApiResponse<List<Map<String, Object>>> listRuns(@PathVariable Long kbId) {
        return ApiResponse.ok(kbDataQualityService.listRuns(TenantContext.requireOrgId(), kbId));
    }

    @GetMapping("/knowledge-bases/{kbId}/issues")
    public ApiResponse<List<Map<String, Object>>> listIssues(@PathVariable Long kbId,
                                                              @RequestParam(name = "status", required = false) String status) {
        return ApiResponse.ok(kbDataQualityService.listIssues(TenantContext.requireOrgId(), kbId, status));
    }

    private String currentUserId() {
        return TenantContext.getUserId().orElse("org-admin");
    }
}
