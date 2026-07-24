package com.codehouse.ciciassistant.openapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_api_task")
public class AgentApiTaskEntity {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCEL_REQUESTED = "CANCEL_REQUESTED";

    @Id
    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "credential_id", nullable = false)
    private Long credentialId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "external_user_id", length = 128)
    private String externalUserId;

    @Column(name = "external_session_id", length = 160)
    private String externalSessionId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AgentApiTaskEntity() {
    }

    public AgentApiTaskEntity(String taskId,
                              String requestId,
                              String companyId,
                              Long credentialId,
                              String agentId,
                              String externalUserId,
                              String externalSessionId) {
        this.taskId = taskId;
        this.requestId = requestId;
        this.companyId = companyId;
        this.credentialId = credentialId;
        this.agentId = agentId;
        this.externalUserId = externalUserId;
        this.externalSessionId = externalSessionId;
        this.status = STATUS_RUNNING;
        this.cancelRequested = false;
        this.createdAt = Instant.now();
    }

    public String getTaskId() { return taskId; }
    public String getRequestId() { return requestId; }
    public String getCompanyId() { return companyId; }
    public Long getCredentialId() { return credentialId; }
    public String getAgentId() { return agentId; }
    public String getExternalUserId() { return externalUserId; }
    public String getExternalSessionId() { return externalSessionId; }
    public String getStatus() { return status; }
    public boolean isCancelRequested() { return cancelRequested; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void completeSuccess() {
        this.status = STATUS_SUCCESS;
        this.completedAt = Instant.now();
    }

    public void completeFailure() {
        this.status = STATUS_FAILED;
        this.completedAt = Instant.now();
    }

    public void markCancelRequested() {
        this.status = STATUS_CANCEL_REQUESTED;
        this.cancelRequested = true;
        this.completedAt = Instant.now();
    }
}
