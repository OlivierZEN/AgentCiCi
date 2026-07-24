package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_quality_run")
public class KbQualityRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "trigger_type", nullable = false, length = 32)
    private String triggerType;

    @Column(name = "scanned_chunk_count", nullable = false)
    private int scannedChunkCount;

    @Column(name = "duplicate_issue_count", nullable = false)
    private int duplicateIssueCount;

    @Column(name = "invalid_issue_count", nullable = false)
    private int invalidIssueCount;

    @Column(name = "regex_issue_count", nullable = false)
    private int regexIssueCount;

    @Column(name = "total_issue_count", nullable = false)
    private int totalIssueCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KbQualityRunEntity() {
    }

    public KbQualityRunEntity(String companyId, Long knowledgeBaseId, String triggerType, String createdBy) {
        this.companyId = companyId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.status = "RUNNING";
        this.triggerType = triggerType;
        this.createdBy = createdBy;
        this.startedAt = Instant.now();
        this.createdAt = this.startedAt;
    }

    public Long getId() { return id; }
    public String getCompanyId() { return companyId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public String getStatus() { return status; }
    public String getTriggerType() { return triggerType; }
    public int getScannedChunkCount() { return scannedChunkCount; }
    public int getDuplicateIssueCount() { return duplicateIssueCount; }
    public int getInvalidIssueCount() { return invalidIssueCount; }
    public int getRegexIssueCount() { return regexIssueCount; }
    public int getTotalIssueCount() { return totalIssueCount; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void complete(int scannedChunks, int duplicateIssues, int invalidIssues, int regexIssues) {
        this.scannedChunkCount = scannedChunks;
        this.duplicateIssueCount = duplicateIssues;
        this.invalidIssueCount = invalidIssues;
        this.regexIssueCount = regexIssues;
        this.totalIssueCount = duplicateIssues + invalidIssues + regexIssues;
        this.status = "COMPLETED";
        this.finishedAt = Instant.now();
    }

    public void fail(String message) {
        this.status = "FAILED";
        this.errorMessage = message == null ? "" : message.substring(0, Math.min(1000, message.length()));
        this.finishedAt = Instant.now();
    }
}
