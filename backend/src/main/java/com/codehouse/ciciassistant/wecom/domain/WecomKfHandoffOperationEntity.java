package com.codehouse.ciciassistant.wecom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wecom_kf_handoff_operation")
public class WecomKfHandoffOperationEntity {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String CONFLICT = "CONFLICT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", nullable = false, unique = true)
    private UUID operationId = UUID.randomUUID();

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "actor_userid", nullable = false, length = 128)
    private String actorUserId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "expected_revision", nullable = false)
    private long expectedRevision;

    @Column(name = "resulting_revision")
    private Long resultingRevision;

    @Column(name = "old_state", nullable = false)
    private int oldState;

    @Column(name = "target_state", nullable = false)
    private int targetState;

    @Column(name = "readback_state")
    private Integer readbackState;

    @Column(name = "status", nullable = false, length = 24)
    private String status = IN_PROGRESS;

    @Column(name = "reason", length = 64)
    private String reason;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected WecomKfHandoffOperationEntity() {
    }

    public WecomKfHandoffOperationEntity(String companyId,
                                         Long conversationId,
                                         String actorUserId,
                                         String idempotencyKey,
                                         String correlationId,
                                         long expectedRevision,
                                         int oldState,
                                         int targetState,
                                         String reason) {
        this.companyId = companyId;
        this.conversationId = conversationId;
        this.actorUserId = actorUserId;
        this.idempotencyKey = idempotencyKey;
        this.correlationId = correlationId;
        this.expectedRevision = expectedRevision;
        this.oldState = oldState;
        this.targetState = targetState;
        this.reason = reason;
    }

    public void succeed(int readbackState, long resultingRevision) {
        this.readbackState = readbackState;
        this.resultingRevision = resultingRevision;
        this.status = SUCCEEDED;
        this.completedAt = Instant.now();
    }

    public void fail(String errorCode, Integer readbackState, long resultingRevision) {
        this.errorCode = errorCode;
        this.readbackState = readbackState;
        this.resultingRevision = resultingRevision;
        this.status = FAILED;
        this.completedAt = Instant.now();
    }

    public void conflict(String errorCode, long resultingRevision) {
        this.errorCode = errorCode;
        this.resultingRevision = resultingRevision;
        this.status = CONFLICT;
        this.completedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getOperationId() { return operationId; }
    public String getCompanyId() { return companyId; }
    public Long getConversationId() { return conversationId; }
    public String getActorUserId() { return actorUserId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getCorrelationId() { return correlationId; }
    public long getExpectedRevision() { return expectedRevision; }
    public Long getResultingRevision() { return resultingRevision; }
    public int getOldState() { return oldState; }
    public int getTargetState() { return targetState; }
    public Integer getReadbackState() { return readbackState; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public String getErrorCode() { return errorCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
