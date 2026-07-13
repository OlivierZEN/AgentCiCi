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

    @Column(name = "failure_category", length = 64)
    private String failureCategory;

    @Column(name = "failure_summary", length = 1000)
    private String failureSummary;

    @Column(name = "assertion_results_json", columnDefinition = "TEXT")
    private String assertionResultsJson;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "elapsed_ms", nullable = false)
    private Long elapsedMs;

    @Column(name = "tool_call_count", nullable = false)
    private Integer toolCallCount;

    @Column(name = "rag_hit_count", nullable = false)
    private Integer ragHitCount;

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
        this.score = STATUS_PASSED.equals(status) ? 1.0d : 0.0d;
        this.elapsedMs = 0L;
        this.toolCallCount = 0;
        this.ragHitCount = 0;
    }

    public void attachEvidence(String failureCategory,
                               String failureSummary,
                               String assertionResultsJson,
                               String traceId,
                               double score,
                               long elapsedMs,
                               int toolCallCount,
                               int ragHitCount) {
        this.failureCategory = failureCategory;
        this.failureSummary = failureSummary;
        this.assertionResultsJson = assertionResultsJson;
        this.traceId = traceId;
        this.score = score;
        this.elapsedMs = elapsedMs;
        this.toolCallCount = toolCallCount;
        this.ragHitCount = ragHitCount;
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

    public String getFailureCategory() { return failureCategory; }

    public String getFailureSummary() { return failureSummary; }

    public String getAssertionResultsJson() { return assertionResultsJson; }

    public String getTraceId() { return traceId; }

    public Double getScore() { return score; }

    public Long getElapsedMs() { return elapsedMs; }

    public Integer getToolCallCount() { return toolCallCount; }

    public Integer getRagHitCount() { return ragHitCount; }
}
