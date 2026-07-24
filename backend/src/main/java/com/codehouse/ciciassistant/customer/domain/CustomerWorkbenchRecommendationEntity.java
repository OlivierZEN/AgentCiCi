package com.codehouse.ciciassistant.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "customer_workbench_recommendation")
public class CustomerWorkbenchRecommendationEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_APPLYING = "APPLYING";
    public static final String STATUS_DISMISSED = "DISMISSED";
    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "crm_account_id", nullable = false, length = 128)
    private String crmAccountId;

    @Column(name = "recommendation_type", nullable = false, length = 48)
    private String recommendationType;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "rationale", nullable = false, columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "confidence", nullable = false)
    private BigDecimal confidence;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "crm_payload", nullable = false, columnDefinition = "TEXT")
    private String crmPayload;

    @Column(name = "applied_crm_id", length = 128)
    private String appliedCrmId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "target_object", length = 64)
    private String targetObject;

    @Column(name = "target_record_id", length = 128)
    private String targetRecordId;

    @Column(name = "evidence_json", nullable = false, columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "dismissal_reason", columnDefinition = "TEXT")
    private String dismissalReason;

    @Column(name = "confirmed_by", length = 64)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "last_error_code", length = 128)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "source_event_id", length = 64)
    private String sourceEventId;

    @Column(name = "source_batch_id", length = 64)
    private String sourceBatchId;

    @Column(name = "action_key", length = 128)
    private String actionKey;

    @Column(name = "trigger_type", length = 32)
    private String triggerType;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerWorkbenchRecommendationEntity() {
    }

    public CustomerWorkbenchRecommendationEntity(String publicId,
                                                 String companyId,
                                                 String crmAccountId,
                                                 String recommendationType,
                                                 String title,
                                                 String rationale,
                                                 BigDecimal confidence,
                                                 String crmPayload) {
        this.publicId = publicId;
        this.companyId = companyId;
        this.crmAccountId = crmAccountId;
        this.recommendationType = recommendationType;
        this.title = title;
        this.rationale = rationale;
        this.confidence = confidence;
        this.status = STATUS_PENDING;
        this.crmPayload = crmPayload;
        this.version = 0L;
        this.targetObject = "";
        this.evidenceJson = "[]";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getCompanyId() { return companyId; }
    public String getCrmAccountId() { return crmAccountId; }
    public String getRecommendationType() { return recommendationType; }
    public String getTitle() { return title; }
    public String getRationale() { return rationale; }
    public BigDecimal getConfidence() { return confidence; }
    public String getStatus() { return status; }
    public String getCrmPayload() { return crmPayload; }
    public String getAppliedCrmId() { return appliedCrmId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
    public String getTargetObject() { return targetObject; }
    public String getTargetRecordId() { return targetRecordId; }
    public String getEvidenceJson() { return evidenceJson; }
    public String getDismissalReason() { return dismissalReason; }
    public String getConfirmedBy() { return confirmedBy; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getAppliedAt() { return appliedAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public String getSourceEventId() { return sourceEventId; }
    public String getSourceBatchId() { return sourceBatchId; }
    public String getActionKey() { return actionKey; }
    public String getTriggerType() { return triggerType; }
    public Instant getValidUntil() { return validUntil; }

    public void configureTarget(String targetObject, String targetRecordId, String evidenceJson) {
        this.targetObject = targetObject;
        this.targetRecordId = targetRecordId;
        this.evidenceJson = evidenceJson == null || evidenceJson.isBlank() ? "[]" : evidenceJson;
        this.updatedAt = Instant.now();
    }

    public void configureTrigger(String sourceEventId, String sourceBatchId, String actionKey,
                                 String triggerType, Instant validUntil) {
        this.sourceEventId = sourceEventId;
        this.sourceBatchId = sourceBatchId;
        this.actionKey = actionKey;
        this.triggerType = triggerType;
        this.validUntil = validUntil;
        this.updatedAt = Instant.now();
    }

    public void refreshPending(String title, String rationale, BigDecimal confidence, String crmPayload,
                               String targetObject, String targetRecordId, String evidenceJson,
                               String sourceEventId, String sourceBatchId, Instant validUntil) {
        if (!STATUS_PENDING.equals(status) && !STATUS_FAILED.equals(status)) return;
        this.title = title;
        this.rationale = rationale;
        this.confidence = confidence;
        this.crmPayload = crmPayload;
        this.targetObject = targetObject;
        this.targetRecordId = targetRecordId;
        this.evidenceJson = evidenceJson;
        this.sourceEventId = sourceEventId;
        this.sourceBatchId = sourceBatchId;
        this.validUntil = validUntil;
        this.status = STATUS_PENDING;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void accept() {
        if (!STATUS_PENDING.equals(this.status)) {
            throw new IllegalStateException("只有待处理建议可以采纳");
        }
        this.status = STATUS_ACCEPTED;
        this.dismissalReason = null;
        this.updatedAt = Instant.now();
    }

    public void updateDraft(String title, String rationale, BigDecimal confidence, String crmPayload,
                            String targetObject, String targetRecordId) {
        if (STATUS_APPLIED.equals(status) || STATUS_APPLYING.equals(status)) {
            throw new IllegalStateException("已执行建议不能修改");
        }
        this.title = title;
        this.rationale = rationale;
        this.confidence = confidence;
        this.crmPayload = crmPayload;
        this.targetObject = targetObject;
        this.targetRecordId = targetRecordId;
        this.status = STATUS_PENDING;
        this.updatedAt = Instant.now();
    }

    public void dismiss(String reason) {
        if (STATUS_APPLIED.equals(status) || STATUS_APPLYING.equals(status)) {
            throw new IllegalStateException("执行中的建议不能忽略");
        }
        this.status = STATUS_DISMISSED;
        this.dismissalReason = reason;
        this.updatedAt = Instant.now();
    }

    public void confirm(String userId) {
        if (!STATUS_ACCEPTED.equals(status)) {
            throw new IllegalStateException("建议必须先采纳，才能确认");
        }
        this.status = STATUS_CONFIRMED;
        this.confirmedBy = userId;
        this.confirmedAt = Instant.now();
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = this.confirmedAt;
    }

    public void markApplying() {
        if (!STATUS_CONFIRMED.equals(status) && !STATUS_FAILED.equals(status)) {
            throw new IllegalStateException("建议必须确认后才能执行");
        }
        this.status = STATUS_APPLYING;
        this.updatedAt = Instant.now();
    }

    public void apply(String appliedCrmId) {
        this.status = STATUS_APPLIED;
        this.appliedCrmId = appliedCrmId;
        this.appliedAt = Instant.now();
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = this.appliedAt;
    }

    public void markFailed(String errorCode, String errorMessage) {
        this.status = STATUS_FAILED;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    public void expire() {
        if (STATUS_PENDING.equals(status) || STATUS_FAILED.equals(status)) {
            this.status = STATUS_EXPIRED;
            this.updatedAt = Instant.now();
        }
    }
}
