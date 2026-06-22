package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_eval_run")
public class AgentEvalRunEntity {

    public static final String STATUS_PASSED = "PASSED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_EMPTY = "EMPTY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "suite_id", nullable = false)
    private Long suiteId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "case_count", nullable = false)
    private Integer caseCount;

    @Column(name = "passed_count", nullable = false)
    private Integer passedCount;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount;

    @Column(name = "p0_failed_count", nullable = false)
    private Integer p0FailedCount;

    @Column(name = "safety_failed_count", nullable = false)
    private Integer safetyFailedCount;

    @Column(name = "pass_rate", nullable = false)
    private Double passRate;

    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentEvalRunEntity() {
    }

    public AgentEvalRunEntity(String orgId,
                              String agentId,
                              Long suiteId,
                              Integer versionNo,
                              String status,
                              Integer caseCount,
                              Integer passedCount,
                              Integer failedCount,
                              Integer p0FailedCount,
                              Integer safetyFailedCount,
                              Double passRate,
                              String summaryJson,
                              Instant startedAt,
                              Instant finishedAt) {
        this.orgId = orgId;
        this.agentId = agentId;
        this.suiteId = suiteId;
        this.versionNo = versionNo;
        this.status = status;
        this.caseCount = caseCount;
        this.passedCount = passedCount;
        this.failedCount = failedCount;
        this.p0FailedCount = p0FailedCount;
        this.safetyFailedCount = safetyFailedCount;
        this.passRate = passRate;
        this.summaryJson = summaryJson;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.createdAt = startedAt;
    }

    public Long getId() { return id; }

    public String getOrgId() { return orgId; }

    public String getAgentId() { return agentId; }

    public Long getSuiteId() { return suiteId; }

    public Integer getVersionNo() { return versionNo; }

    public String getStatus() { return status; }

    public Integer getCaseCount() { return caseCount; }

    public Integer getPassedCount() { return passedCount; }

    public Integer getFailedCount() { return failedCount; }

    public Integer getP0FailedCount() { return p0FailedCount; }

    public Integer getSafetyFailedCount() { return safetyFailedCount; }

    public Double getPassRate() { return passRate; }

    public String getSummaryJson() { return summaryJson; }

    public Instant getStartedAt() { return startedAt; }

    public Instant getFinishedAt() { return finishedAt; }

    public Instant getCreatedAt() { return createdAt; }
}
