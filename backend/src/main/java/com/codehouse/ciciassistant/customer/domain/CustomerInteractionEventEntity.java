package com.codehouse.ciciassistant.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_interaction_event")
public class CustomerInteractionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "crm_account_id", nullable = false, length = 128)
    private String crmAccountId;

    @Column(name = "crm_contact_id", length = 128)
    private String crmContactId;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "subject", nullable = false, length = 256)
    private String subject;

    @Column(name = "raw_summary", nullable = false, columnDefinition = "TEXT")
    private String rawSummary;

    @Column(name = "ai_summary", nullable = false, columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "sentiment", nullable = false, length = 32)
    private String sentiment;

    @Column(name = "intent_tags", nullable = false, columnDefinition = "TEXT")
    private String intentTags;

    @Column(name = "lifecycle_area", nullable = false, length = 32)
    private String lifecycleArea;

    @Column(name = "source_batch_id", length = 64)
    private String sourceBatchId;

    @Column(name = "analysis_json", nullable = false, columnDefinition = "TEXT")
    private String analysisJson;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount;

    @Column(name = "analysis_version", nullable = false)
    private int analysisVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerInteractionEventEntity() {
    }

    public CustomerInteractionEventEntity(String publicId,
                                          String companyId,
                                          String crmAccountId,
                                          String crmContactId,
                                          String sourceType,
                                          Instant occurredAt,
                                          String subject,
                                          String rawSummary,
                                          String aiSummary,
                                          String sentiment,
                                          String intentTags,
                                          String lifecycleArea) {
        this.publicId = publicId;
        this.companyId = companyId;
        this.crmAccountId = crmAccountId;
        this.crmContactId = crmContactId;
        this.sourceType = sourceType;
        this.occurredAt = occurredAt;
        this.subject = subject;
        this.rawSummary = rawSummary;
        this.aiSummary = aiSummary;
        this.sentiment = sentiment;
        this.intentTags = intentTags;
        this.lifecycleArea = lifecycleArea;
        this.analysisJson = "{}";
        this.evidenceCount = 0;
        this.analysisVersion = 1;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getCompanyId() { return companyId; }
    public String getCrmAccountId() { return crmAccountId; }
    public String getCrmContactId() { return crmContactId; }
    public String getSourceType() { return sourceType; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getSubject() { return subject; }
    public String getRawSummary() { return rawSummary; }
    public String getAiSummary() { return aiSummary; }
    public String getSentiment() { return sentiment; }
    public String getIntentTags() { return intentTags; }
    public String getLifecycleArea() { return lifecycleArea; }
    public String getSourceBatchId() { return sourceBatchId; }
    public String getAnalysisJson() { return analysisJson; }
    public int getEvidenceCount() { return evidenceCount; }
    public int getAnalysisVersion() { return analysisVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void attachArchive(String sourceBatchId, String analysisJson, int evidenceCount, int analysisVersion) {
        this.sourceBatchId = sourceBatchId;
        this.analysisJson = analysisJson == null || analysisJson.isBlank() ? "{}" : analysisJson;
        this.evidenceCount = Math.max(0, evidenceCount);
        this.analysisVersion = Math.max(1, analysisVersion);
        this.updatedAt = Instant.now();
    }
}
