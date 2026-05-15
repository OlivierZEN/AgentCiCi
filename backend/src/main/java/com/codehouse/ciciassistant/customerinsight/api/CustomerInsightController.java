package com.codehouse.ciciassistant.customerinsight.api;

import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.customerinsight.service.CustomerInsightService;
import com.codehouse.ciciassistant.customerinsight.service.CustomerInsightService.ProjectCommand;
import com.codehouse.ciciassistant.customerinsight.service.CustomerInsightService.SectionCommand;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/customer-insights")
public class CustomerInsightController {

    private final CustomerInsightService customerInsightService;

    public CustomerInsightController(CustomerInsightService customerInsightService) {
        this.customerInsightService = customerInsightService;
    }

    @GetMapping("/catalog")
    public ApiResponse<List<Map<String, Object>>> catalog() {
        return ApiResponse.ok(customerInsightService.catalog());
    }

    @GetMapping("/projects")
    public ApiResponse<List<Map<String, Object>>> listProjects() {
        return ApiResponse.ok(customerInsightService.listProjects(TenantContext.requireOrgId()));
    }

    @PostMapping("/projects")
    public ApiResponse<Map<String, Object>> createProject(@Valid @RequestBody ProjectRequest request) {
        return ApiResponse.ok(customerInsightService.createProject(
                TenantContext.requireOrgId(),
                requireUserId(),
                request.toCommand()
        ));
    }

    @GetMapping("/projects/{projectId}")
    public ApiResponse<Map<String, Object>> projectDetail(@PathVariable String projectId) {
        return ApiResponse.ok(customerInsightService.getProject(TenantContext.requireOrgId(), projectId));
    }

    @PatchMapping("/projects/{projectId}")
    public ApiResponse<Map<String, Object>> updateProject(@PathVariable String projectId,
                                                          @RequestBody ProjectRequest request) {
        return ApiResponse.ok(customerInsightService.updateProject(
                TenantContext.requireOrgId(),
                projectId,
                request.toCommand()
        ));
    }

    @DeleteMapping("/projects/{projectId}")
    public ApiResponse<Void> deleteProject(@PathVariable String projectId) {
        customerInsightService.deleteProject(TenantContext.requireOrgId(), projectId);
        return ApiResponse.okMessage("客户洞察项目已删除");
    }

    @PostMapping("/projects/{projectId}/refresh-sources")
    public ApiResponse<Map<String, Object>> refreshSources(@PathVariable String projectId) {
        return ApiResponse.ok(customerInsightService.refreshSources(TenantContext.requireOrgId(), projectId));
    }

    @PutMapping("/projects/{projectId}/sections/{sectionCode}")
    public ApiResponse<Map<String, Object>> saveSection(@PathVariable String projectId,
                                                        @PathVariable String sectionCode,
                                                        @RequestBody SectionRequest request) {
        return ApiResponse.ok(customerInsightService.saveSection(
                TenantContext.requireOrgId(),
                projectId,
                sectionCode,
                request.toCommand()
        ));
    }

    @PostMapping("/projects/{projectId}/sections/{sectionCode}/generate")
    public ApiResponse<Map<String, Object>> generateSection(@PathVariable String projectId,
                                                            @PathVariable String sectionCode,
                                                            @RequestBody(required = false) SectionRequest request) {
        return ApiResponse.ok(customerInsightService.generateSection(
                TenantContext.requireOrgId(),
                requireUserId(),
                projectId,
                sectionCode,
                request == null ? new SectionCommand(Map.of(), null) : request.toCommand()
        ));
    }

    @PostMapping("/projects/{projectId}/generate-full")
    public ApiResponse<Map<String, Object>> generateFull(@PathVariable String projectId) {
        return ApiResponse.ok(customerInsightService.generateFull(
                TenantContext.requireOrgId(),
                requireUserId(),
                projectId
        ));
    }

    @GetMapping("/projects/{projectId}/jobs/{jobId}")
    public ApiResponse<Map<String, Object>> job(@PathVariable String projectId, @PathVariable Long jobId) {
        return ApiResponse.ok(customerInsightService.getJob(TenantContext.requireOrgId(), projectId, jobId));
    }

    private String requireUserId() {
        return TenantContext.getUserId().orElseThrow(() -> new IllegalArgumentException("Missing user context"));
    }

    public record ProjectRequest(
            @NotBlank String customerName,
            String customerExternalId,
            String customerObjectApiName,
            String industry,
            String sourceType) {
        ProjectCommand toCommand() {
            return new ProjectCommand(customerName, customerExternalId, customerObjectApiName, industry, sourceType);
        }
    }

    public record SectionRequest(Map<String, Object> input, String markdown) {
        SectionCommand toCommand() {
            return new SectionCommand(input == null ? Map.of() : input, markdown);
        }
    }
}
