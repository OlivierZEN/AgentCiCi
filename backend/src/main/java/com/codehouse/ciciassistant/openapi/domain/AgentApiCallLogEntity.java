package com.codehouse.ciciassistant.openapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_api_call_log")
public class AgentApiCallLogEntity {

    public static final String STATUS_STARTED = "STARTED";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "credential_id", nullable = false)
    private Long credentialId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "run_as_user_id", nullable = false, length = 64)
    private String runAsUserId;

    @Column(name = "external_session_id", length = 160)
    private String externalSessionId;

    @Column(name = "internal_session_id", nullable = false, length = 64)
    private String internalSessionId;

    @Column(name = "external_user_id", length = 128)
    private String externalUserId;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "prompt_chars", nullable = false)
    private int promptChars;

    @Column(name = "response_chars", nullable = false)
    private int responseChars;

    @Column(name = "elapsed_ms", nullable = false)
    private int elapsedMs;

    @Column(name = "request_summary", columnDefinition = "TEXT")
    private String requestSummary;

    @Column(name = "response_summary", columnDefinition = "TEXT")
    private String responseSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AgentApiCallLogEntity() {
    }

    public AgentApiCallLogEntity(String requestId,
                                 String orgId,
                                 Long credentialId,
                                 String agentId,
                                 String runAsUserId,
                                 String externalSessionId,
                                 String internalSessionId,
                                 String externalUserId,
                                 String clientIp,
                                 String idempotencyKey,
                                 int promptChars,
                                 String requestSummary) {
        this.requestId = requestId;
        this.orgId = orgId;
        this.credentialId = credentialId;
        this.agentId = agentId;
        this.runAsUserId = runAsUserId;
        this.externalSessionId = externalSessionId;
        this.internalSessionId = internalSessionId;
        this.externalUserId = externalUserId;
        this.clientIp = clientIp;
        this.idempotencyKey = idempotencyKey;
        this.status = STATUS_STARTED;
        this.httpStatus = 102;
        this.promptChars = promptChars;
        this.responseChars = 0;
        this.elapsedMs = 0;
        this.requestSummary = requestSummary;
        this.createdAt = Instant.now();
    }

    public String getRequestId() { return requestId; }
    public String getOrgId() { return orgId; }
    public Long getCredentialId() { return credentialId; }
    public String getAgentId() { return agentId; }
    public String getRunAsUserId() { return runAsUserId; }
    public String getExternalSessionId() { return externalSessionId; }
    public String getInternalSessionId() { return internalSessionId; }
    public String getExternalUserId() { return externalUserId; }
    public String getClientIp() { return clientIp; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public int getHttpStatus() { return httpStatus; }
    public String getErrorCode() { return errorCode; }
    public String getTraceId() { return traceId; }
    public int getPromptChars() { return promptChars; }
    public int getResponseChars() { return responseChars; }
    public int getElapsedMs() { return elapsedMs; }
    public String getRequestSummary() { return requestSummary; }
    public String getResponseSummary() { return responseSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void completeSuccess(int httpStatus,
                                String traceId,
                                int responseChars,
                                int elapsedMs,
                                String responseSummary) {
        this.status = STATUS_SUCCESS;
        this.httpStatus = httpStatus;
        this.errorCode = null;
        this.traceId = traceId;
        this.responseChars = Math.max(0, responseChars);
        this.elapsedMs = Math.max(0, elapsedMs);
        this.responseSummary = responseSummary;
        this.completedAt = Instant.now();
    }

    public void completeFailure(int httpStatus, String errorCode, int elapsedMs, String responseSummary) {
        this.status = STATUS_FAILED;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.elapsedMs = Math.max(0, elapsedMs);
        this.responseSummary = responseSummary;
        this.completedAt = Instant.now();
    }
}
