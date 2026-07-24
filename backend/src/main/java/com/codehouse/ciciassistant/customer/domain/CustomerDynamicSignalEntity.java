package com.codehouse.ciciassistant.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_dynamic_signal")
public class CustomerDynamicSignalEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;
    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;
    @Column(name = "crm_account_id", nullable = false, length = 128)
    private String crmAccountId;
    @Column(name = "source_event_id", nullable = false, length = 64)
    private String sourceEventId;
    @Column(name = "source_batch_id", length = 64)
    private String sourceBatchId;
    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;
    @Column(name = "dimension", nullable = false, length = 32)
    private String dimension;
    @Column(name = "direction", nullable = false, length = 16)
    private String direction;
    @Column(name = "impact", nullable = false)
    private int impact;
    @Column(name = "confidence", nullable = false)
    private double confidence;
    @Column(name = "title", nullable = false, length = 256)
    private String title;
    @Column(name = "rationale", nullable = false, columnDefinition = "TEXT")
    private String rationale;
    @Column(name = "evidence_quote", nullable = false, columnDefinition = "TEXT")
    private String evidenceQuote;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "valid_until")
    private Instant validUntil;
    @Column(name = "content_fingerprint", nullable = false, length = 64)
    private String contentFingerprint;
    @Column(name = "model_version", nullable = false, length = 64)
    private String modelVersion;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerDynamicSignalEntity() {}

    public CustomerDynamicSignalEntity(String publicId, String companyId, String crmAccountId,
                                       String sourceEventId, String sourceBatchId, String sourceType,
                                       String dimension, String direction, int impact, double confidence,
                                       String title, String rationale, String evidenceQuote,
                                       Instant occurredAt, Instant validUntil, String contentFingerprint,
                                       String modelVersion) {
        this.publicId = publicId;
        this.companyId = companyId;
        this.crmAccountId = crmAccountId;
        this.sourceEventId = sourceEventId;
        this.sourceBatchId = sourceBatchId;
        refresh(sourceType, dimension, direction, impact, confidence, title, rationale, evidenceQuote,
                occurredAt, validUntil, contentFingerprint, modelVersion);
        this.createdAt = Instant.now();
    }

    public void refresh(String sourceType, String dimension, String direction, int impact, double confidence,
                        String title, String rationale, String evidenceQuote, Instant occurredAt,
                        Instant validUntil, String contentFingerprint, String modelVersion) {
        this.sourceType = sourceType;
        this.dimension = dimension;
        this.direction = direction;
        this.impact = Math.max(1, Math.min(10, impact));
        this.confidence = Math.max(0, Math.min(1, confidence));
        this.title = title;
        this.rationale = rationale;
        this.evidenceQuote = evidenceQuote;
        this.occurredAt = occurredAt;
        this.validUntil = validUntil;
        this.contentFingerprint = contentFingerprint;
        this.modelVersion = modelVersion;
        this.status = this.confidence >= 0.65 ? STATUS_ACTIVE : STATUS_PENDING;
        this.updatedAt = Instant.now();
    }

    public void supersede() { this.status = STATUS_SUPERSEDED; this.updatedAt = Instant.now(); }
    public void expire() { this.status = STATUS_EXPIRED; this.updatedAt = Instant.now(); }

    public String getPublicId() { return publicId; }
    public String getCompanyId() { return companyId; }
    public String getCrmAccountId() { return crmAccountId; }
    public String getSourceEventId() { return sourceEventId; }
    public String getSourceBatchId() { return sourceBatchId; }
    public String getSourceType() { return sourceType; }
    public String getDimension() { return dimension; }
    public String getDirection() { return direction; }
    public int getImpact() { return impact; }
    public double getConfidence() { return confidence; }
    public String getTitle() { return title; }
    public String getRationale() { return rationale; }
    public String getEvidenceQuote() { return evidenceQuote; }
    public String getStatus() { return status; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getValidUntil() { return validUntil; }
    public String getContentFingerprint() { return contentFingerprint; }
    public String getModelVersion() { return modelVersion; }
}
