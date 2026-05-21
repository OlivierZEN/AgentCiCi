package com.codehouse.ciciassistant.openapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_api_message")
public class AgentApiMessageEntity {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "credential_id", nullable = false)
    private Long credentialId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "external_user_id", length = 128)
    private String externalUserId;

    @Column(name = "external_session_id", length = 160)
    private String externalSessionId;

    @Column(name = "internal_session_id", nullable = false, length = 64)
    private String internalSessionId;

    @Column(name = "query", columnDefinition = "TEXT")
    private String query;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AgentApiMessageEntity() {
    }

    public AgentApiMessageEntity(String messageId,
                                 String requestId,
                                 String taskId,
                                 String orgId,
                                 Long credentialId,
                                 String agentId,
                                 String externalUserId,
                                 String externalSessionId,
                                 String internalSessionId,
                                 String query,
                                 String answer,
                                 String status,
                                 String errorCode,
                                 String idempotencyKey,
                                 String metadataJson) {
        this.messageId = messageId;
        this.requestId = requestId;
        this.taskId = taskId;
        this.orgId = orgId;
        this.credentialId = credentialId;
        this.agentId = agentId;
        this.externalUserId = externalUserId;
        this.externalSessionId = externalSessionId;
        this.internalSessionId = internalSessionId;
        this.query = query;
        this.answer = answer;
        this.status = status;
        this.errorCode = errorCode;
        this.idempotencyKey = idempotencyKey;
        this.metadataJson = metadataJson;
        this.createdAt = Instant.now();
        this.completedAt = Instant.now();
    }

    public String getMessageId() { return messageId; }
    public String getRequestId() { return requestId; }
    public String getTaskId() { return taskId; }
    public String getOrgId() { return orgId; }
    public Long getCredentialId() { return credentialId; }
    public String getAgentId() { return agentId; }
    public String getExternalUserId() { return externalUserId; }
    public String getExternalSessionId() { return externalSessionId; }
    public String getInternalSessionId() { return internalSessionId; }
    public String getQuery() { return query; }
    public String getAnswer() { return answer; }
    public String getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getMetadataJson() { return metadataJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
