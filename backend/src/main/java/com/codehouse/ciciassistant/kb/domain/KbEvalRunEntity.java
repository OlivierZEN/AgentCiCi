package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_eval_run")
public class KbEvalRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "suite_id", nullable = false)
    private Long suiteId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "case_count", nullable = false)
    private int caseCount;

    @Column(name = "passed_count", nullable = false)
    private int passedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "hit_rate", nullable = false)
    private double hitRate;

    @Column(name = "expected_source_recall", nullable = false)
    private double expectedSourceRecall;

    @Column(name = "forbidden_source_violations", nullable = false)
    private int forbiddenSourceViolations;

    @Column(name = "average_top_score", nullable = false)
    private double averageTopScore;

    @Column(name = "stale_source_rate", nullable = false)
    private double staleSourceRate;

    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KbEvalRunEntity() {
    }

    public KbEvalRunEntity(String companyId, Long suiteId, Long knowledgeBaseId, int caseCount) {
        Instant now = Instant.now();
        this.companyId = companyId;
        this.suiteId = suiteId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.status = "RUNNING";
        this.caseCount = caseCount;
        this.startedAt = now;
        this.createdAt = now;
    }

    public void finish(String status,
                       int passedCount,
                       int failedCount,
                       double hitRate,
                       double expectedSourceRecall,
                       int forbiddenSourceViolations,
                       double averageTopScore,
                       double staleSourceRate,
                       String summaryJson) {
        this.status = status;
        this.passedCount = passedCount;
        this.failedCount = failedCount;
        this.hitRate = hitRate;
        this.expectedSourceRecall = expectedSourceRecall;
        this.forbiddenSourceViolations = forbiddenSourceViolations;
        this.averageTopScore = averageTopScore;
        this.staleSourceRate = staleSourceRate;
        this.summaryJson = summaryJson;
        this.finishedAt = Instant.now();
    }

    public Long getId() { return id; }

    public String getCompanyId() { return companyId; }

    public Long getSuiteId() { return suiteId; }

    public Long getKnowledgeBaseId() { return knowledgeBaseId; }

    public String getStatus() { return status; }

    public int getCaseCount() { return caseCount; }

    public int getPassedCount() { return passedCount; }

    public int getFailedCount() { return failedCount; }

    public double getHitRate() { return hitRate; }

    public double getExpectedSourceRecall() { return expectedSourceRecall; }

    public int getForbiddenSourceViolations() { return forbiddenSourceViolations; }

    public double getAverageTopScore() { return averageTopScore; }

    public double getStaleSourceRate() { return staleSourceRate; }

    public String getSummaryJson() { return summaryJson; }

    public Instant getStartedAt() { return startedAt; }

    public Instant getFinishedAt() { return finishedAt; }

    public Instant getCreatedAt() { return createdAt; }
}
