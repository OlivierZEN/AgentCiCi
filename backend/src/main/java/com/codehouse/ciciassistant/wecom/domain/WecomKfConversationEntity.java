package com.codehouse.ciciassistant.wecom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "wecom_kf_conversation")
public class WecomKfConversationEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String OWNER_AI = "AI";
    public static final String OWNER_HANDOFF = "HANDOFF";
    public static final String OWNER_PENDING = "PENDING";
    public static final String OWNER_HUMAN = "HUMAN";
    public static final String OWNER_ENDED = "ENDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "corp_id", nullable = false, length = 64)
    private String corpId;

    @Column(name = "open_kfid", nullable = false, length = 128)
    private String openKfId;

    @Column(name = "external_userid", nullable = false, length = 128)
    private String externalUserId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "run_as_user_id", nullable = false, length = 64)
    private String runAsUserId;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "remote_service_state", nullable = false)
    private int remoteServiceState = 0;

    @Column(name = "owner_mode", nullable = false, length = 16)
    private String ownerMode = OWNER_AI;

    @Column(name = "servicer_userid", length = 128)
    private String servicerUserId;

    @Column(name = "state_revision", nullable = false)
    private long stateRevision = 0;

    @Column(name = "state_checked_at")
    private Instant stateCheckedAt;

    @Column(name = "handoff_reason", length = 64)
    private String handoffReason;

    @Column(name = "last_customer_message_at")
    private Instant lastCustomerMessageAt;

    @Column(name = "reply_count_in_window", nullable = false)
    private int replyCountInWindow = 0;

    @Column(name = "status", nullable = false, length = 32)
    private String status = STATUS_ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WecomKfConversationEntity() {
    }

    public WecomKfConversationEntity(String companyId,
                                     String corpId,
                                     String openKfId,
                                     String externalUserId,
                                     String sessionId,
                                     String agentId,
                                     String runAsUserId) {
        this.companyId = companyId;
        this.corpId = corpId;
        this.openKfId = openKfId;
        this.externalUserId = externalUserId;
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.runAsUserId = runAsUserId;
    }

    public Long getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getCorpId() { return corpId; }
    public String getOpenKfId() { return openKfId; }
    public String getExternalUserId() { return externalUserId; }
    public String getSessionId() { return sessionId; }
    public String getAgentId() { return agentId; }
    public String getRunAsUserId() { return runAsUserId; }
    public UUID getPublicId() { return publicId; }
    public int getRemoteServiceState() { return remoteServiceState; }
    public String getOwnerMode() { return ownerMode; }
    public String getServicerUserId() { return servicerUserId; }
    public long getStateRevision() { return stateRevision; }
    public Instant getStateCheckedAt() { return stateCheckedAt; }
    public String getHandoffReason() { return handoffReason; }
    public Instant getLastCustomerMessageAt() { return lastCustomerMessageAt; }
    public int getReplyCountInWindow() { return replyCountInWindow; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markCustomerMessage(Instant messageAt) {
        this.lastCustomerMessageAt = messageAt == null ? Instant.now() : messageAt;
        this.replyCountInWindow = 0;
        this.updatedAt = Instant.now();
    }

    public boolean canReply(Instant now) {
        if (lastCustomerMessageAt == null || replyCountInWindow >= 5) {
            return false;
        }
        Instant current = now == null ? Instant.now() : now;
        return !lastCustomerMessageAt.isBefore(current.minus(Duration.ofHours(48)));
    }

    public void markReplySent() {
        this.replyCountInWindow += 1;
        this.updatedAt = Instant.now();
    }

    public boolean aiOwned() {
        return OWNER_AI.equals(ownerMode) && (remoteServiceState == 0 || remoteServiceState == 1);
    }

    public void reserveHandoff(String reason) {
        this.ownerMode = OWNER_HANDOFF;
        this.handoffReason = blank(reason);
        this.stateRevision += 1;
        this.updatedAt = Instant.now();
    }

    public void synchronizeRemoteState(int remoteState, String servicerUserId, String reason, Instant checkedAt) {
        if (remoteState < 0 || remoteState > 4) {
            throw new IllegalArgumentException("Unsupported customer service state: " + remoteState);
        }
        String normalizedServicer = blank(servicerUserId);
        String nextOwner = OWNER_HANDOFF.equals(this.ownerMode)
                && (remoteState == 0 || remoteState == 1)
                && !"handoff_failed".equals(reason)
                ? OWNER_HANDOFF
                : ownerFor(remoteState);
        boolean changed = remoteServiceState != remoteState
                || !Objects.equals(this.servicerUserId, normalizedServicer)
                || !Objects.equals(this.ownerMode, nextOwner);
        this.remoteServiceState = remoteState;
        this.ownerMode = nextOwner;
        this.servicerUserId = normalizedServicer;
        this.handoffReason = blank(reason);
        this.stateCheckedAt = checkedAt == null ? Instant.now() : checkedAt;
        if (changed) {
            this.stateRevision += 1;
        }
        this.updatedAt = Instant.now();
    }

    private String ownerFor(int remoteState) {
        return switch (remoteState) {
            case 0, 1 -> OWNER_AI;
            case 2 -> OWNER_PENDING;
            case 3 -> OWNER_HUMAN;
            case 4 -> OWNER_ENDED;
            default -> throw new IllegalArgumentException("Unsupported customer service state: " + remoteState);
        };
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
