package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_task_review")
public class AgentTaskReviewEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "org_id", nullable = false, length = 64) private String orgId;
    @Column(name = "run_id", nullable = false) private Long runId;
    @Column(name = "review_round", nullable = false) private int reviewRound;
    @Column(name = "gate_status", nullable = false, length = 24) private String gateStatus;
    @Column(name = "reviewer_status", nullable = false, length = 24) private String reviewerStatus;
    @Column(name = "issue_codes_json", nullable = false, columnDefinition = "TEXT") private String issueCodesJson;
    @Column(name = "result_summary", nullable = false, length = 1024) private String resultSummary;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected AgentTaskReviewEntity() { }
    public AgentTaskReviewEntity(String orgId, long runId, int reviewRound, String gateStatus,
                                 String reviewerStatus, String issueCodesJson, String resultSummary, Instant createdAt) {
        this.orgId = orgId; this.runId = runId; this.reviewRound = reviewRound; this.gateStatus = gateStatus;
        this.reviewerStatus = reviewerStatus; this.issueCodesJson = issueCodesJson; this.resultSummary = resultSummary;
        this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public String getOrgId() { return orgId; }
    public Long getRunId() { return runId; }
    public int getReviewRound() { return reviewRound; }
    public String getGateStatus() { return gateStatus; }
    public String getReviewerStatus() { return reviewerStatus; }
    public String getIssueCodesJson() { return issueCodesJson; }
    public String getResultSummary() { return resultSummary; }
    public Instant getCreatedAt() { return createdAt; }
}
