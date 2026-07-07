package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_annotation_suggestion")
public class KbAnnotationSuggestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "chunk_id")
    private Long chunkId;

    @Column(name = "field_key", nullable = false, length = 64)
    private String fieldKey;

    @Column(name = "suggested_value", nullable = false, length = 1024)
    private String suggestedValue;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "rationale")
    private String rationale;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KbAnnotationSuggestionEntity() {
    }

    public KbAnnotationSuggestionEntity(String orgId, Long knowledgeBaseId, String targetType, Long targetId,
                                        Long documentId, Long chunkId, String fieldKey, String suggestedValue,
                                        double confidence, String source, String rationale) {
        this.orgId = orgId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.fieldKey = fieldKey;
        this.suggestedValue = suggestedValue;
        this.confidence = confidence;
        this.source = source;
        this.rationale = rationale;
        this.status = "PENDING";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public String getOrgId() { return orgId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public Long getDocumentId() { return documentId; }
    public Long getChunkId() { return chunkId; }
    public String getFieldKey() { return fieldKey; }
    public String getSuggestedValue() { return suggestedValue; }
    public double getConfidence() { return confidence; }
    public String getSource() { return source; }
    public String getRationale() { return rationale; }
    public String getStatus() { return status; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void accept(String actor, String value) {
        this.suggestedValue = value == null || value.isBlank() ? this.suggestedValue : value.trim();
        this.status = "ACCEPTED";
        this.reviewedBy = actor;
        this.reviewedAt = Instant.now();
        this.updatedAt = this.reviewedAt;
    }

    public void reject(String actor) {
        this.status = "REJECTED";
        this.reviewedBy = actor;
        this.reviewedAt = Instant.now();
        this.updatedAt = this.reviewedAt;
    }
}
