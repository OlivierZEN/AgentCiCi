package com.codehouse.ciciassistant.ontology.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyLifecycleService;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyLifecycleService.BindingView;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyLifecycleService.ImportProposal;
import com.codehouse.ciciassistant.ontology.semattice.SematticeOntologyLifecycleService.OperationView;
import com.codehouse.ciciassistant.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireOrgAdmin
@RequestMapping("/admin/ontologies/{workspaceId}/semattice")
public class AdminSematticeOntologyController {

    private final SematticeOntologyLifecycleService service;

    public AdminSematticeOntologyController(SematticeOntologyLifecycleService service) {
        this.service = service;
    }

    @PostMapping("/link")
    public ApiResponse<BindingView> link(@PathVariable Long workspaceId) {
        return ApiResponse.ok(service.link(companyId(), userId(), workspaceId));
    }

    @GetMapping("/status")
    public ApiResponse<BindingView> status(@PathVariable Long workspaceId) {
        return ApiResponse.ok(service.status(companyId(), workspaceId));
    }

    @PostMapping("/drift-check")
    public ApiResponse<BindingView> driftCheck(@PathVariable Long workspaceId) {
        return ApiResponse.ok(service.checkDrift(companyId(), userId(), workspaceId));
    }

    @PostMapping("/import-proposal")
    public ApiResponse<ImportProposal> importProposal(@PathVariable Long workspaceId) {
        return ApiResponse.ok(service.importProposal(companyId(), userId(), workspaceId));
    }

    @PostMapping("/compile")
    public ApiResponse<OperationView> compile(
            @PathVariable Long workspaceId,
            @RequestBody RevisionRequest request) {
        return ApiResponse.ok(service.prepare(
                companyId(), userId(), workspaceId,
                request == null || request.expectedRevision() == null
                        ? -1L
                        : request.expectedRevision()));
    }

    @PostMapping("/operations/{operationId}/approval-request")
    public ApiResponse<OperationView> requestApproval(
            @PathVariable Long workspaceId,
            @PathVariable String operationId) {
        return ApiResponse.ok(service.requestApproval(
                companyId(), userId(), workspaceId, operationId));
    }

    @PostMapping("/operations/{operationId}/activate")
    public ApiResponse<OperationView> activate(
            @PathVariable Long workspaceId,
            @PathVariable String operationId) {
        return ApiResponse.ok(service.activate(
                companyId(), userId(), workspaceId, operationId));
    }

    @PostMapping("/operations/{operationId}/cancel")
    public ApiResponse<OperationView> cancel(
            @PathVariable Long workspaceId,
            @PathVariable String operationId) {
        return ApiResponse.ok(service.cancel(
                companyId(), userId(), workspaceId, operationId));
    }

    @PostMapping("/operations/{operationId}/rollback-prepare")
    public ApiResponse<OperationView> prepareRollback(
            @PathVariable Long workspaceId,
            @PathVariable String operationId) {
        return ApiResponse.ok(service.prepareRollback(
                companyId(), userId(), workspaceId, operationId));
    }

    @GetMapping("/operations/{operationId}")
    public ApiResponse<OperationView> operation(
            @PathVariable Long workspaceId,
            @PathVariable String operationId) {
        return ApiResponse.ok(service.operation(companyId(), userId(), workspaceId, operationId));
    }

    @GetMapping("/operations/latest")
    public ApiResponse<OperationView> latestOperation(@PathVariable Long workspaceId) {
        return ApiResponse.ok(service.latestOperation(companyId(), userId(), workspaceId));
    }

    private String companyId() {
        return TenantContext.requireCompanyId();
    }

    private String userId() {
        return TenantContext.getUserId()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new ForbiddenException("ONTOLOGY_USER_REQUIRED"));
    }

    public record RevisionRequest(Long expectedRevision) {
    }
}
