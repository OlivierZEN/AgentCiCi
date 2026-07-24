package com.codehouse.ciciassistant.embed.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "meeting_session")
public class MeetingSessionEntity {

    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_SUMMARIZING = "SUMMARIZING";
    public static final String STATUS_READY_TO_WRITEBACK = "READY_TO_WRITEBACK";
    public static final String STATUS_WRITTEN_BACK = "WRITTEN_BACK";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "token_nonce", nullable = false, length = 128)
    private String tokenNonce;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "external_user_id", length = 128)
    private String externalUserId;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "app_code", nullable = false, length = 64)
    private String appCode;

    @Column(name = "object_type", nullable = false, length = 96)
    private String objectType;

    @Column(name = "object_id", nullable = false, length = 160)
    private String objectId;

    @Column(name = "record_name", length = 256)
    private String recordName;

    @Column(name = "customer_name", length = 256)
    private String customerName;

    @Column(name = "parent_origin", nullable = false, length = 256)
    private String parentOrigin;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "context_json", nullable = false, columnDefinition = "TEXT")
    private String contextJson;

    @Column(name = "summary_markdown", columnDefinition = "TEXT")
    private String summaryMarkdown;

    @Column(name = "writeback_preview_json", columnDefinition = "TEXT")
    private String writebackPreviewJson;

    @Column(name = "writeback_result_json", columnDefinition = "TEXT")
    private String writebackResultJson;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MeetingSessionEntity() {
    }

    public MeetingSessionEntity(String id,
                                String tokenNonce,
                                String companyId,
                                String userId,
                                String externalUserId,
                                String source,
                                String appCode,
                                String objectType,
                                String objectId,
                                String recordName,
                                String customerName,
                                String parentOrigin,
                                String contextJson) {
        this.id = id;
        this.tokenNonce = tokenNonce;
        this.companyId = companyId;
        this.userId = userId;
        this.externalUserId = externalUserId;
        this.source = source;
        this.appCode = appCode;
        this.objectType = objectType;
        this.objectId = objectId;
        this.recordName = recordName;
        this.customerName = customerName;
        this.parentOrigin = parentOrigin;
        this.status = STATUS_CREATED;
        this.contextJson = contextJson;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTokenNonce() { return tokenNonce; }
    public String getCompanyId() { return companyId; }
    public String getUserId() { return userId; }
    public String getExternalUserId() { return externalUserId; }
    public String getSource() { return source; }
    public String getAppCode() { return appCode; }
    public String getObjectType() { return objectType; }
    public String getObjectId() { return objectId; }
    public String getRecordName() { return recordName; }
    public String getCustomerName() { return customerName; }
    public String getParentOrigin() { return parentOrigin; }
    public String getStatus() { return status; }
    public String getContextJson() { return contextJson; }
    public String getSummaryMarkdown() { return summaryMarkdown; }
    public String getWritebackPreviewJson() { return writebackPreviewJson; }
    public String getWritebackResultJson() { return writebackResultJson; }
    public String getTraceId() { return traceId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markSummarizing() {
        this.status = STATUS_SUMMARIZING;
        this.updatedAt = Instant.now();
    }

    public void markSummaryReady(String summaryMarkdown) {
        this.status = STATUS_READY_TO_WRITEBACK;
        this.summaryMarkdown = summaryMarkdown;
        this.updatedAt = Instant.now();
    }

    public void markWritebackPreview(String previewJson) {
        this.status = STATUS_READY_TO_WRITEBACK;
        this.writebackPreviewJson = previewJson;
        this.updatedAt = Instant.now();
    }

    public void markWritebackResult(String resultJson, boolean completed) {
        this.status = completed ? STATUS_WRITTEN_BACK : STATUS_READY_TO_WRITEBACK;
        this.writebackResultJson = resultJson;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String message) {
        this.status = STATUS_FAILED;
        this.writebackResultJson = message;
        this.updatedAt = Instant.now();
    }
}
