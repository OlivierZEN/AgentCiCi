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

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

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

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "baseline_version_no")
    private Integer baselineVersionNo;

    @Column(name = "trigger_type", nullable = false, length = 32)
    private String triggerType;

    @Column(name = "runtime_snapshot_json", columnDefinition = "TEXT")
    private String runtimeSnapshotJson;

    @Column(name = "snapshot_fingerprint", length = 128)
    private String snapshotFingerprint;

    @Column(name = "avg_latency_ms", nullable = false)
    private Long avgLatencyMs;

    @Column(name = "total_elapsed_ms", nullable = false)
    private Long totalElapsedMs;

    @Column(name = "tool_call_accuracy", nullable = false)
    private Double toolCallAccuracy;

    @Column(name = "rag_hit_rate", nullable = false)
    private Double ragHitRate;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    protected AgentEvalRunEntity() {
    }

    public AgentEvalRunEntity(String companyId,
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
        this.companyId = companyId;
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
        this.targetType = "CANDIDATE";
        this.triggerType = "MANUAL";
        this.avgLatencyMs = 0L;
        this.totalElapsedMs = 0L;
        this.toolCallAccuracy = 0.0d;
        this.ragHitRate = 0.0d;
    }

    public void attachExecutionMetadata(String targetType,
                                        Integer baselineVersionNo,
                                        String triggerType,
                                        String runtimeSnapshotJson,
                                        String snapshotFingerprint,
                                        long avgLatencyMs,
                                        long totalElapsedMs,
                                        double toolCallAccuracy,
                                        double ragHitRate,
                                        String createdBy) {
        this.targetType = targetType == null || targetType.isBlank() ? "CANDIDATE" : targetType;
        this.baselineVersionNo = baselineVersionNo;
        this.triggerType = triggerType == null || triggerType.isBlank() ? "MANUAL" : triggerType;
        this.runtimeSnapshotJson = runtimeSnapshotJson;
        this.snapshotFingerprint = snapshotFingerprint;
        this.avgLatencyMs = avgLatencyMs;
        this.totalElapsedMs = totalElapsedMs;
        this.toolCallAccuracy = toolCallAccuracy;
        this.ragHitRate = ragHitRate;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }

    public String getCompanyId() { return companyId; }

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

    public String getTargetType() { return targetType; }

    public Integer getBaselineVersionNo() { return baselineVersionNo; }

    public String getTriggerType() { return triggerType; }

    public String getRuntimeSnapshotJson() { return runtimeSnapshotJson; }

    public String getSnapshotFingerprint() { return snapshotFingerprint; }

    public Long getAvgLatencyMs() { return avgLatencyMs; }

    public Long getTotalElapsedMs() { return totalElapsedMs; }

    public Double getToolCallAccuracy() { return toolCallAccuracy; }

    public Double getRagHitRate() { return ragHitRate; }

    public String getCreatedBy() { return createdBy; }
}
