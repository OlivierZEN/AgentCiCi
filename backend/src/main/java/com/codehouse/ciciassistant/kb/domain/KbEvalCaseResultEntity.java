package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_eval_case_result")
public class KbEvalCaseResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "expected_hit", nullable = false)
    private boolean expectedHit;

    @Column(name = "forbidden_violation", nullable = false)
    private boolean forbiddenViolation;

    @Column(name = "stale_source", nullable = false)
    private boolean staleSource;

    @Column(name = "top_score", nullable = false)
    private double topScore;

    @Column(name = "matched_document_id")
    private Long matchedDocumentId;

    @Column(name = "matched_chunk_id")
    private Long matchedChunkId;

    @Column(name = "result_summary_json", columnDefinition = "TEXT")
    private String resultSummaryJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KbEvalCaseResultEntity() {
    }

    public KbEvalCaseResultEntity(String companyId,
                                  Long runId,
                                  Long caseId,
                                  String status,
                                  boolean expectedHit,
                                  boolean forbiddenViolation,
                                  boolean staleSource,
                                  double topScore,
                                  Long matchedDocumentId,
                                  Long matchedChunkId,
                                  String resultSummaryJson) {
        this.companyId = companyId;
        this.runId = runId;
        this.caseId = caseId;
        this.status = status;
        this.expectedHit = expectedHit;
        this.forbiddenViolation = forbiddenViolation;
        this.staleSource = staleSource;
        this.topScore = topScore;
        this.matchedDocumentId = matchedDocumentId;
        this.matchedChunkId = matchedChunkId;
        this.resultSummaryJson = resultSummaryJson;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }

    public String getCompanyId() { return companyId; }

    public Long getRunId() { return runId; }

    public Long getCaseId() { return caseId; }

    public String getStatus() { return status; }

    public boolean isExpectedHit() { return expectedHit; }

    public boolean isForbiddenViolation() { return forbiddenViolation; }

    public boolean isStaleSource() { return staleSource; }

    public double getTopScore() { return topScore; }

    public Long getMatchedDocumentId() { return matchedDocumentId; }

    public Long getMatchedChunkId() { return matchedChunkId; }

    public String getResultSummaryJson() { return resultSummaryJson; }

    public Instant getCreatedAt() { return createdAt; }
}
