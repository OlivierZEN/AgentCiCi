package com.codehouse.ciciassistant.userworkflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_workflow_execution")
public class UserWorkflowExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "trigger_id")
    private Long triggerId;

    @Column(name = "routine_key", nullable = false, length = 128)
    private String routineKey;

    @Column(name = "trigger_source", nullable = false, length = 32)
    private String triggerSource;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "trace_json", nullable = false, columnDefinition = "TEXT")
    private String traceJson;

    @Column(name = "output_summary", columnDefinition = "TEXT")
    private String outputSummary;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserWorkflowExecutionEntity() {
    }

    public UserWorkflowExecutionEntity(String companyId,
                                       String userId,
                                       String agentId,
                                       Long versionId,
                                       Long triggerId,
                                       String routineKey,
                                       String triggerSource,
                                       Instant scheduledAt) {
        this.companyId = companyId;
        this.userId = userId;
        this.agentId = agentId;
        this.versionId = versionId;
        this.triggerId = triggerId;
        this.routineKey = routineKey;
        this.triggerSource = triggerSource;
        this.scheduledAt = scheduledAt;
        this.status = "QUEUED";
        this.traceJson = "[]";
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getUserId() {
        return userId;
    }

    public String getAgentId() {
        return agentId;
    }

    public Long getVersionId() {
        return versionId;
    }

    public Long getTriggerId() {
        return triggerId;
    }

    public String getRoutineKey() {
        return routineKey;
    }

    public String getTriggerSource() {
        return triggerSource;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getStatus() {
        return status;
    }

    public String getTraceJson() {
        return traceJson;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markRunning() {
        this.status = "RUNNING";
        this.startedAt = Instant.now();
    }

    public void markSuccess(String traceJson, String outputSummary) {
        this.status = "SUCCESS";
        this.finishedAt = Instant.now();
        this.traceJson = traceJson;
        this.outputSummary = outputSummary;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markFailed(String traceJson, String errorCode, String errorMessage) {
        this.status = "FAILED";
        this.finishedAt = Instant.now();
        this.traceJson = traceJson;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
