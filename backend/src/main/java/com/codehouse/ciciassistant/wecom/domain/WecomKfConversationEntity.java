package com.codehouse.ciciassistant.wecom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "wecom_kf_conversation")
public class WecomKfConversationEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

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
}
