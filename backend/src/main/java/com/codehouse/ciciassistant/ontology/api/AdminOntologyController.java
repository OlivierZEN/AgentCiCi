package com.codehouse.ciciassistant.ontology.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.ontology.service.OntologyManagementService;
import com.codehouse.ciciassistant.ontology.service.OntologyManagementService.*;
import com.codehouse.ciciassistant.ontology.service.OntologyAiProposalService.ProposalView;
import com.codehouse.ciciassistant.ontology.service.OntologyReferencePackageService.ReferencePackageSummary;
import com.codehouse.ciciassistant.ontology.service.OntologyValidationService;
import com.codehouse.ciciassistant.ontology.service.OntologyManagementService.DataSourceMutationRequest;
import com.codehouse.ciciassistant.ontology.service.OntologyManagementService.DiscoverFieldsRequest;
import com.codehouse.ciciassistant.ontology.service.OntologyManagementService.DraftSaveRequest;
import com.codehouse.ciciassistant.ontology.service.OntologyManagementService.MappingReplaceRequest;
import com.codehouse.ciciassistant.ontology.service.OntologyManagementService.MappingValidationRequest;
import com.codehouse.ciciassistant.ontology.service.OntologyManagementService.ProposalRequest;
import com.codehouse.ciciassistant.ontology.service.OntologyManagementService.RevisionRequest;
import com.codehouse.ciciassistant.ontology.service.OntologyManagementService.WorkspaceCreateRequest;
import com.codehouse.ciciassistant.ontology.service.OntologyManagementService.WorkspaceUpdateRequest;
import com.codehouse.ciciassistant.tenant.TenantContext;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ontologies")
@RequireOrgAdmin
public class AdminOntologyController {

    private final OntologyManagementService management;

    public AdminOntologyController(OntologyManagementService management) {
        this.management = management;
    }

    @GetMapping
    public ApiResponse<List<WorkspaceView>> list() {
        return ApiResponse.ok(management.listWorkspaces());
    }

    @PostMapping
    public ApiResponse<WorkspaceView> create(@RequestBody WorkspaceCreateRequest request) {
        return ApiResponse.ok(management.createWorkspace(currentUser(), request));
    }

    @GetMapping("/{workspaceId}")
    public ApiResponse<WorkspaceView> get(@PathVariable Long workspaceId) {
        return ApiResponse.ok(management.getWorkspace(workspaceId));
    }

    @PatchMapping("/{workspaceId}")
    public ApiResponse<WorkspaceView> update(
            @PathVariable Long workspaceId,
            @RequestBody WorkspaceUpdateRequest request) {
        return ApiResponse.ok(management.updateWorkspace(currentUser(), workspaceId, request));
    }

    @PostMapping("/{workspaceId}/archive")
    public ApiResponse<WorkspaceView> archive(
            @PathVariable Long workspaceId,
            @RequestBody RevisionRequest request) {
        return ApiResponse.ok(management.archiveWorkspace(currentUser(), workspaceId, request));
    }

    @GetMapping("/{workspaceId}/draft")
    public ApiResponse<DraftView> getDraft(@PathVariable Long workspaceId) {
        return ApiResponse.ok(management.getDraft(workspaceId));
    }

    @PutMapping("/{workspaceId}/draft")
    public ApiResponse<DraftView> saveDraft(
            @PathVariable Long workspaceId,
            @RequestBody DraftSaveRequest request) {
        return ApiResponse.ok(management.saveDraft(currentUser(), workspaceId, request));
    }

    @PostMapping("/{workspaceId}/draft/validate")
    public ApiResponse<List<OntologyValidationService.ValidationIssue>> validateDraft(
            @PathVariable Long workspaceId) {
        return ApiResponse.ok(management.validateDraft(workspaceId));
    }

    @GetMapping("/{workspaceId}/draft/diff")
    public ApiResponse<DraftDiffView> diffDraft(@PathVariable Long workspaceId) {
        return ApiResponse.ok(management.diffDraft(workspaceId));
    }

    @PostMapping("/{workspaceId}/proposals")
    public ApiResponse<ProposalView> createProposal(
            @PathVariable Long workspaceId,
            @RequestBody ProposalRequest request) {
        return ApiResponse.ok(management.createProposal(currentUser(), workspaceId, request));
    }

    @GetMapping("/{workspaceId}/proposals")
    public ApiResponse<List<ProposalRecordView>> listProposals(@PathVariable Long workspaceId) {
        return ApiResponse.ok(management.listProposals(workspaceId));
    }

    @GetMapping("/{workspaceId}/proposals/{proposalId}")
    public ApiResponse<ProposalRecordView> getProposal(
            @PathVariable Long workspaceId,
            @PathVariable Long proposalId) {
        return ApiResponse.ok(management.getProposal(workspaceId, proposalId));
    }

    @PostMapping("/{workspaceId}/proposals/{proposalId}/apply")
    public ApiResponse<ProposalView> applyProposal(
            @PathVariable Long workspaceId,
            @PathVariable Long proposalId,
            @RequestBody RevisionRequest request) {
        return ApiResponse.ok(management.applyProposal(
                currentUser(), workspaceId, proposalId, request));
    }

    @GetMapping("/reference-packages")
    public ApiResponse<List<ReferencePackageSummary>> listReferencePackages() {
        return ApiResponse.ok(management.listReferencePackages());
    }

    @GetMapping("/reference-packages/{packageId}")
    public ApiResponse<ReferencePackageView> getReferencePackage(@PathVariable String packageId) {
        return ApiResponse.ok(management.getReferencePackage(packageId));
    }

    @PostMapping("/reference-packages/{packageId}/install")
    public ApiResponse<WorkspaceView> installReferencePackage(@PathVariable String packageId) {
        return ApiResponse.ok(management.installReferencePackage(currentUser(), packageId));
    }

    @GetMapping("/{workspaceId}/data-sources")
    public ApiResponse<List<SourceView>> listDataSources(@PathVariable Long workspaceId) {
        return ApiResponse.ok(management.listDataSources(workspaceId));
    }

    @PostMapping("/{workspaceId}/data-sources")
    public ApiResponse<DraftView> createDataSource(
            @PathVariable Long workspaceId,
            @RequestBody DataSourceMutationRequest request) {
        return ApiResponse.ok(management.createDataSource(currentUser(), workspaceId, request));
    }

    @PutMapping("/{workspaceId}/data-sources/{dataSourceId}")
    public ApiResponse<DraftView> updateDataSource(
            @PathVariable Long workspaceId,
            @PathVariable Long dataSourceId,
            @RequestBody DataSourceMutationRequest request) {
        return ApiResponse.ok(management.updateDataSource(
                currentUser(), workspaceId, dataSourceId, request));
    }

    @PostMapping("/{workspaceId}/data-sources/{dataSourceId}/discover-objects")
    public ApiResponse<CatalogObjectMutationView> discoverObjects(
            @PathVariable Long workspaceId,
            @PathVariable Long dataSourceId,
            @RequestBody RevisionRequest request) {
        return ApiResponse.ok(management.discoverObjects(
                currentUser(), workspaceId, dataSourceId, request));
    }

    @PostMapping("/{workspaceId}/data-sources/{dataSourceId}/discover-fields")
    public ApiResponse<CatalogFieldMutationView> discoverFields(
            @PathVariable Long workspaceId,
            @PathVariable Long dataSourceId,
            @RequestBody DiscoverFieldsRequest request) {
        return ApiResponse.ok(management.discoverFields(
                currentUser(), workspaceId, dataSourceId, request));
    }

    @GetMapping("/{workspaceId}/catalog")
    public ApiResponse<CatalogView> getCatalog(@PathVariable Long workspaceId) {
        return ApiResponse.ok(management.getCatalog(workspaceId));
    }

    @GetMapping("/{workspaceId}/mappings")
    public ApiResponse<List<MappingView>> listMappings(@PathVariable Long workspaceId) {
        return ApiResponse.ok(management.listMappings(workspaceId));
    }

    @PutMapping("/{workspaceId}/mappings")
    public ApiResponse<DraftView> replaceMappings(
            @PathVariable Long workspaceId,
            @RequestBody MappingReplaceRequest request) {
        return ApiResponse.ok(management.replaceMappings(currentUser(), workspaceId, request));
    }

    @PostMapping("/{workspaceId}/mappings/validate")
    public ApiResponse<MappingValidationBatchView> validateMappings(
            @PathVariable Long workspaceId,
            @RequestBody MappingValidationRequest request) {
        return ApiResponse.ok(management.validateMappings(currentUser(), workspaceId, request));
    }

    @PostMapping("/{workspaceId}/compile-preview")
    public ApiResponse<CompilePreviewView> compilePreview(
            @PathVariable Long workspaceId,
            @RequestBody RevisionRequest request) {
        return ApiResponse.ok(management.compilePreview(workspaceId, request));
    }

    @PostMapping("/{workspaceId}/publish")
    public ApiResponse<VersionSummaryView> publish(
            @PathVariable Long workspaceId,
            @RequestBody RevisionRequest request) {
        return ApiResponse.ok(management.publish(currentUser(), workspaceId, request));
    }

    @GetMapping("/{workspaceId}/versions")
    public ApiResponse<List<VersionSummaryView>> listVersions(@PathVariable Long workspaceId) {
        return ApiResponse.ok(management.listVersions(workspaceId));
    }

    @GetMapping("/{workspaceId}/versions/{versionNo}")
    public ApiResponse<VersionDetailView> getVersion(
            @PathVariable Long workspaceId,
            @PathVariable Integer versionNo) {
        return ApiResponse.ok(management.getVersion(workspaceId, versionNo));
    }

    private String currentUser() {
        return TenantContext.getUserId()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new ForbiddenException("ONTOLOGY_USER_REQUIRED"));
    }
}
