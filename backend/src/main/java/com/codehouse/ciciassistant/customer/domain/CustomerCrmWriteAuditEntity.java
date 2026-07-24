package com.codehouse.ciciassistant.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_crm_write_audit")
public class CustomerCrmWriteAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;
    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    @Column(name = "recommendation_id", nullable = false, length = 64)
    private String recommendationId;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Column(name = "target_object", nullable = false, length = 64)
    private String targetObject;
    @Column(name = "operation", nullable = false, length = 32)
    private String operation;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;
    @Column(name = "remote_record_id", length = 128)
    private String remoteRecordId;
    @Column(name = "error_code", length = 128)
    private String errorCode;
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    @Column(name = "request_summary", nullable = false, columnDefinition = "TEXT")
    private String requestSummary;
    @Column(name = "response_summary", nullable = false, columnDefinition = "TEXT")
    private String responseSummary;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CustomerCrmWriteAuditEntity() {}

    public CustomerCrmWriteAuditEntity(String publicId, String companyId, String userId, String recommendationId,
                                       String idempotencyKey, String targetObject, String operation, String status,
                                       String requestHash, String remoteRecordId, String errorCode, String errorMessage,
                                       String requestSummary, String responseSummary) {
        this.publicId = publicId;
        this.companyId = companyId;
        this.userId = userId;
        this.recommendationId = recommendationId;
        this.idempotencyKey = idempotencyKey;
        this.targetObject = targetObject;
        this.operation = operation;
        this.status = status;
        this.requestHash = requestHash;
        this.remoteRecordId = remoteRecordId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.requestSummary = requestSummary;
        this.responseSummary = responseSummary;
        this.createdAt = Instant.now();
    }

    public String getStatus() { return status; }
    public String getRemoteRecordId() { return remoteRecordId; }

    public void markStarted() {
        this.status = "STARTED";
        this.remoteRecordId = null;
        this.errorCode = null;
        this.errorMessage = null;
        this.responseSummary = "{}";
    }

    public void markSucceeded(String remoteRecordId, String responseSummary) {
        this.status = "SUCCEEDED";
        this.remoteRecordId = remoteRecordId;
        this.errorCode = null;
        this.errorMessage = null;
        this.responseSummary = responseSummary == null ? "{}" : responseSummary;
    }

    public void markFailed(String errorCode, String errorMessage, boolean resultUnknown) {
        this.status = resultUnknown ? "UNKNOWN" : "FAILED";
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.responseSummary = "{}";
    }
}
