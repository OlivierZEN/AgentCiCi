package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_eval_case_result")
public class AgentEvalCaseResultEntity {

    public static final String STATUS_PASSED = "PASSED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "assertion_type", nullable = false, length = 64)
    private String assertionType;

    @Column(name = "actual_status", length = 64)
    private String actualStatus;

    @Column(name = "output_preview", columnDefinition = "TEXT")
    private String outputPreview;

    @Column(name = "result_summary_json", columnDefinition = "TEXT")
    private String resultSummaryJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentEvalCaseResultEntity() {
    }

    public AgentEvalCaseResultEntity(String orgId,
                                     String agentId,
                                     Long runId,
                                     Long caseId,
                                     Integer versionNo,
                                     String status,
                                     String assertionType,
                                     String actualStatus,
                                     String outputPreview,
                                     String resultSummaryJson) {
        this.orgId = orgId;
        this.agentId = agentId;
        this.runId = runId;
        this.caseId = caseId;
        this.versionNo = versionNo;
        this.status = status;
        this.assertionType = assertionType;
        this.actualStatus = actualStatus;
        this.outputPreview = outputPreview;
        this.resultSummaryJson = resultSummaryJson;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }

    public String getOrgId() { return orgId; }

    public String getAgentId() { return agentId; }

    public Long getRunId() { return runId; }

    public Long getCaseId() { return caseId; }

    public Integer getVersionNo() { return versionNo; }

    public String getStatus() { return status; }

    public String getAssertionType() { return assertionType; }

    public String getActualStatus() { return actualStatus; }

    public String getOutputPreview() { return outputPreview; }

    public String getResultSummaryJson() { return resultSummaryJson; }

    public Instant getCreatedAt() { return createdAt; }
}
