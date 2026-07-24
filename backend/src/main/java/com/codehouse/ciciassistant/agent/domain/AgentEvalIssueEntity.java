package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_eval_issue")
public class AgentEvalIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "run_id")
    private Long runId;

    @Column(name = "case_id")
    private Long caseId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "root_cause_type", nullable = false, length = 64)
    private String rootCauseType;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;

    @Column(name = "owner_user_id", length = 128)
    private String ownerUserId;

    @Column(name = "fix_version_no")
    private Integer fixVersionNo;

    @Column(name = "verification_run_id")
    private Long verificationRunId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentEvalIssueEntity() {
    }

    public AgentEvalIssueEntity(String companyId,
                                String agentId,
                                Long runId,
                                Long caseId,
                                String title,
                                String rootCauseType,
                                String severity,
                                String description,
                                String createdBy) {
        Instant now = Instant.now();
        this.companyId = companyId;
        this.agentId = agentId;
        this.runId = runId;
        this.caseId = caseId;
        this.title = title;
        this.status = "OPEN";
        this.rootCauseType = rootCauseType;
        this.severity = severity;
        this.description = description;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getAgentId() { return agentId; }
    public Long getRunId() { return runId; }
    public Long getCaseId() { return caseId; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getRootCauseType() { return rootCauseType; }
    public String getSeverity() { return severity; }
    public String getOwnerUserId() { return ownerUserId; }
    public Integer getFixVersionNo() { return fixVersionNo; }
    public Long getVerificationRunId() { return verificationRunId; }
    public String getDescription() { return description; }
    public String getResolution() { return resolution; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String status,
                       String rootCauseType,
                       String severity,
                       String ownerUserId,
                       Integer fixVersionNo,
                       Long verificationRunId,
                       String description,
                       String resolution) {
        this.status = status;
        this.rootCauseType = rootCauseType;
        this.severity = severity;
        this.ownerUserId = ownerUserId;
        this.fixVersionNo = fixVersionNo;
        this.verificationRunId = verificationRunId;
        this.description = description;
        this.resolution = resolution;
        this.updatedAt = Instant.now();
    }
}
