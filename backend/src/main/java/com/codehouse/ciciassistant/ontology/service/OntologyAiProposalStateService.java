package com.codehouse.ciciassistant.ontology.service;

import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.ontology.domain.OntologyAiProposalEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyAiProposalRepository;
import com.codehouse.ciciassistant.ontology.domain.OntologyTenantPersistence;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceEntity;
import com.codehouse.ciciassistant.ontology.domain.OntologyWorkspaceRepository;
import com.codehouse.ciciassistant.ontology.model.OntologyDocument;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OntologyAiProposalStateService {

    private static final String INVALID_CODE = "AI_PROPOSAL_INVALID";
    private static final String ARCHIVED_DIAGNOSTIC = "WORKSPACE_ARCHIVED";
    private static final String REVISION_DIAGNOSTIC = "WORKSPACE_REVISION_CHANGED";

    private final OntologyWorkspaceRepository workspaces;
    private final OntologyAiProposalRepository proposals;
    private final OntologyDraftService drafts;
    private final OntologyTenantPersistence persistence;
    private final ObjectMapper objectMapper;

    public OntologyAiProposalStateService(
            OntologyWorkspaceRepository workspaces,
            OntologyAiProposalRepository proposals,
            OntologyDraftService drafts,
            OntologyTenantPersistence persistence,
            ObjectMapper objectMapper) {
        this.workspaces = workspaces;
        this.proposals = proposals;
        this.drafts = drafts;
        this.persistence = persistence;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> BeginResult<T> begin(
            String companyId,
            String userId,
            Long workspaceId,
            String proposalType,
            String instruction,
            Preparation<T> preparation) {
        requireCurrentOrg(companyId);
        OntologyWorkspaceEntity workspace = lockWorkspace(companyId, workspaceId);
        if ("ARCHIVED".equals(workspace.getStatus())) {
            throw invalid();
        }
        OntologyDocument current = drafts.loadDraft(companyId, workspaceId, workspace);
        T prepared = Objects.requireNonNull(preparation, "preparation").prepare(current);
        long baseRevision = workspace.getDraftRevision();
        PendingDiff pendingDiff = new PendingDiff(
                baseRevision, "", List.of(), List.of(), List.of());
        OntologyAiProposalEntity proposal = persistence.saveForCurrentOrg(
                new OntologyAiProposalEntity(
                        companyId,
                        workspaceId,
                        proposalType,
                        instruction,
                        "{}",
                        writeJson(pendingDiff),
                        writeJson(List.of()),
                        userId));
        return new BeginResult<>(proposal, baseRevision, current, prepared);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transition finishReady(
            String companyId,
            Long workspaceId,
            Long proposalId,
            long baseRevision,
            String payloadJson,
            String diffJson,
            String validationJson) {
        requireCurrentOrg(companyId);
        OntologyWorkspaceEntity workspace = lockWorkspace(companyId, workspaceId);
        OntologyAiProposalEntity proposal = lockPendingProposal(companyId, workspaceId, proposalId);
        Transition terminal = terminalWorkspaceFailure(workspace, proposal, baseRevision);
        if (terminal != null) {
            return terminal;
        }
        proposal.markReady(payloadJson, diffJson, validationJson);
        OntologyAiProposalEntity saved = persistence.saveForCurrentOrg(proposal);
        return new Transition(saved, "", "");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transition fail(
            String companyId,
            Long workspaceId,
            Long proposalId,
            long baseRevision,
            String code,
            String diagnostic) {
        requireCurrentOrg(companyId);
        OntologyWorkspaceEntity workspace = lockWorkspace(companyId, workspaceId);
        OntologyAiProposalEntity proposal = proposals
                .findForUpdateByIdAndCompanyId(proposalId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(INVALID_CODE));
        if (!Objects.equals(proposal.getWorkspaceId(), workspaceId)) {
            throw invalid();
        }
        if ("FAILED".equals(proposal.getStatus())) {
            FailureDiagnostic failure = readFailure(proposal.getValidationJson());
            return new Transition(proposal, failure.code(), failure.diagnostic());
        }
        if (!"PENDING".equals(proposal.getStatus())) {
            throw invalid();
        }
        Transition terminal = terminalWorkspaceFailure(workspace, proposal, baseRevision);
        if (terminal != null) {
            return terminal;
        }
        return markFailed(proposal, code, diagnostic);
    }

    private OntologyWorkspaceEntity lockWorkspace(String companyId, Long workspaceId) {
        return workspaces.findForUpdateByIdAndCompanyId(workspaceId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(INVALID_CODE));
    }

    private OntologyAiProposalEntity lockPendingProposal(
            String companyId,
            Long workspaceId,
            Long proposalId) {
        OntologyAiProposalEntity proposal = proposals
                .findForUpdateByIdAndCompanyId(proposalId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(INVALID_CODE));
        if (!Objects.equals(proposal.getWorkspaceId(), workspaceId)
                || !"PENDING".equals(proposal.getStatus())) {
            throw invalid();
        }
        return proposal;
    }

    private Transition terminalWorkspaceFailure(
            OntologyWorkspaceEntity workspace,
            OntologyAiProposalEntity proposal,
            long baseRevision) {
        if ("ARCHIVED".equals(workspace.getStatus())) {
            return markFailed(proposal, INVALID_CODE, ARCHIVED_DIAGNOSTIC);
        }
        if (!Objects.equals(workspace.getDraftRevision(), baseRevision)) {
            return markFailed(proposal, INVALID_CODE, REVISION_DIAGNOSTIC);
        }
        return null;
    }

    private Transition markFailed(
            OntologyAiProposalEntity proposal,
            String code,
            String diagnostic) {
        proposal.markFailed(writeJson(new FailureDiagnostic(code, diagnostic)));
        OntologyAiProposalEntity saved = persistence.saveForCurrentOrg(proposal);
        return new Transition(saved, code, diagnostic);
    }

    private void requireCurrentOrg(String companyId) {
        if (!Objects.equals(TenantContext.requireCompanyId(), companyId)) {
            throw new ForbiddenException(INVALID_CODE);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(INVALID_CODE, exception);
        }
    }

    private FailureDiagnostic readFailure(String value) {
        try {
            FailureDiagnostic failure = objectMapper.readValue(value, FailureDiagnostic.class);
            if (failure.code() == null
                    || failure.code().isBlank()
                    || failure.diagnostic() == null
                    || failure.diagnostic().isBlank()) {
                throw invalid();
            }
            return failure;
        } catch (JsonProcessingException exception) {
            throw invalid();
        }
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException(INVALID_CODE);
    }

    @FunctionalInterface
    public interface Preparation<T> {
        T prepare(OntologyDocument current);
    }

    public record BeginResult<T>(
            OntologyAiProposalEntity proposal,
            long baseRevision,
            OntologyDocument current,
            T prepared) {
    }

    public record Transition(
            OntologyAiProposalEntity proposal,
            String diagnosticCode,
            String diagnosticMessage) {
    }

    private record PendingDiff(
            long baseRevision,
            String candidateHash,
            List<String> added,
            List<String> changed,
            List<String> removed) {
    }

    private record FailureDiagnostic(String code, String diagnostic) {
    }
}
