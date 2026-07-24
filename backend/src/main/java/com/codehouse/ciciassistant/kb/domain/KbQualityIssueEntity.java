package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_quality_issue")
public class KbQualityIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "run_id")
    private Long runId;

    @Column(name = "issue_type", nullable = false, length = 32)
    private String issueType;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "chunk_id")
    private Long chunkId;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "evidence")
    private String evidence;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "resolved_by", length = 64)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KbQualityIssueEntity() {
    }

    public KbQualityIssueEntity(String companyId, Long knowledgeBaseId, Long runId, String issueType, String severity,
                                Long chunkId, Long documentId, Long ruleId, String contentHash, String evidence) {
        this.companyId = companyId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.runId = runId;
        this.issueType = issueType;
        this.severity = severity;
        this.targetType = "CHUNK";
        this.targetId = chunkId;
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.ruleId = ruleId;
        this.contentHash = contentHash;
        this.evidence = evidence;
        this.status = "OPEN";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public String getCompanyId() { return companyId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public Long getRunId() { return runId; }
    public String getIssueType() { return issueType; }
    public String getSeverity() { return severity; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public Long getDocumentId() { return documentId; }
    public Long getChunkId() { return chunkId; }
    public Long getRuleId() { return ruleId; }
    public String getContentHash() { return contentHash; }
    public String getEvidence() { return evidence; }
    public String getStatus() { return status; }
    public String getResolvedBy() { return resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void mark(String status, String actor) {
        this.status = status;
        this.resolvedBy = actor;
        this.resolvedAt = Instant.now();
        this.updatedAt = this.resolvedAt;
    }
}
