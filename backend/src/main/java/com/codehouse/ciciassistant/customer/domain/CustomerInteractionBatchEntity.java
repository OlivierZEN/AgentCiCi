package com.codehouse.ciciassistant.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_interaction_batch")
public class CustomerInteractionBatchEntity {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "crm_account_id", nullable = false, length = 128)
    private String crmAccountId;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "subject", nullable = false, length = 256)
    private String subject;

    @Column(name = "narration_text", nullable = false, columnDefinition = "TEXT")
    private String narrationText;

    @Column(name = "pasted_text", nullable = false, columnDefinition = "TEXT")
    private String pastedText;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "combined_text", nullable = false, columnDefinition = "TEXT")
    private String combinedText;

    @Column(name = "analysis_json", nullable = false, columnDefinition = "TEXT")
    private String analysisJson;

    @Column(name = "error_message", nullable = false, length = 1000)
    private String errorMessage;

    @Column(name = "confirmed_event_id", length = 64)
    private String confirmedEventId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerInteractionBatchEntity() {
    }

    public CustomerInteractionBatchEntity(String publicId, String companyId, String crmAccountId,
                                          String createdBy, String sourceType, Instant occurredAt,
                                          String subject, String narrationText, String pastedText) {
        this.publicId = publicId;
        this.companyId = companyId;
        this.crmAccountId = crmAccountId;
        this.createdBy = createdBy;
        this.sourceType = sourceType;
        this.occurredAt = occurredAt;
        this.subject = subject;
        this.narrationText = narrationText;
        this.pastedText = pastedText;
        this.status = STATUS_QUEUED;
        this.combinedText = "";
        this.analysisJson = "{}";
        this.errorMessage = "";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markProcessing() { status = STATUS_PROCESSING; errorMessage = ""; updatedAt = Instant.now(); }
    public void markProcessed(String combinedText, String analysisJson, boolean partial, String errorMessage) {
        this.combinedText = combinedText;
        this.analysisJson = analysisJson;
        this.status = partial ? STATUS_PARTIAL : STATUS_READY;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
        this.updatedAt = Instant.now();
    }
    public void markFailed(String errorMessage) {
        this.status = STATUS_FAILED;
        this.errorMessage = errorMessage == null ? "处理失败" : errorMessage;
        this.updatedAt = Instant.now();
    }
    public void queueForRetry() { status = STATUS_QUEUED; errorMessage = ""; updatedAt = Instant.now(); }
    public void markConfirmed(String eventId) { status = STATUS_CONFIRMED; confirmedEventId = eventId; updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getCompanyId() { return companyId; }
    public String getCrmAccountId() { return crmAccountId; }
    public String getCreatedBy() { return createdBy; }
    public String getSourceType() { return sourceType; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getSubject() { return subject; }
    public String getNarrationText() { return narrationText; }
    public String getPastedText() { return pastedText; }
    public String getStatus() { return status; }
    public String getCombinedText() { return combinedText; }
    public String getAnalysisJson() { return analysisJson; }
    public String getErrorMessage() { return errorMessage; }
    public String getConfirmedEventId() { return confirmedEventId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

