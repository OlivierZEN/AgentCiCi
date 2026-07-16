package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ontology_ai_proposal")
public class OntologyAiProposalEntity extends AbstractOntologyWorkspaceEntity {

    @Column(name = "proposal_type", nullable = false, length = 32)
    private String proposalType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "instruction", columnDefinition = "TEXT")
    private String instruction;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "diff_json", columnDefinition = "TEXT")
    private String diffJson;

    @Column(name = "validation_json", columnDefinition = "TEXT")
    private String validationJson;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "applied_by", length = 64)
    private String appliedBy;

    @Column(name = "applied_at")
    private Instant appliedAt;

    protected OntologyAiProposalEntity() {
    }

    public OntologyAiProposalEntity(
            String orgId,
            Long workspaceId,
            String proposalType,
            String instruction,
            String payloadJson,
            String diffJson,
            String validationJson,
            String createdBy) {
        super(orgId, workspaceId);
        this.proposalType = proposalType;
        this.status = "PENDING";
        this.instruction = instruction;
        this.payloadJson = payloadJson;
        this.diffJson = diffJson;
        this.validationJson = validationJson;
        this.createdBy = createdBy;
    }

    public String getProposalType() { return proposalType; }
    public String getStatus() { return status; }
    public String getInstruction() { return instruction; }
    public String getPayloadJson() { return payloadJson; }
    public String getDiffJson() { return diffJson; }
    public String getValidationJson() { return validationJson; }
    public String getCreatedBy() { return createdBy; }
    public String getAppliedBy() { return appliedBy; }
    public Instant getAppliedAt() { return appliedAt; }

    public void markReady(String payloadJson, String diffJson, String validationJson) {
        this.status = "READY";
        this.payloadJson = payloadJson;
        this.diffJson = diffJson;
        this.validationJson = validationJson;
        touchUpdatedAt();
    }

    public void markFailed(String validationJson) {
        this.status = "FAILED";
        this.payloadJson = "{}";
        this.validationJson = validationJson;
        touchUpdatedAt();
    }

    public void markApplied(String userId) {
        this.status = "APPLIED";
        this.appliedBy = userId;
        this.appliedAt = Instant.now();
        touchUpdatedAt();
    }
}
