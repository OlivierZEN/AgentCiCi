package com.codehouse.ciciassistant.feishu.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "feishu_bot_binding")
public class FeishuBotBindingEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_UNBOUND = "UNBOUND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "tenant_key", nullable = false, length = 128)
    private String tenantKey;

    @Column(name = "open_id", nullable = false, length = 128)
    private String openId;

    @Column(name = "union_id", length = 128)
    private String unionId;

    @Column(name = "chat_id", length = 128)
    private String chatId;

    @Column(name = "display_name", length = 256)
    private String displayName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "agent_code", nullable = false, length = 64)
    private String agentCode = "cici";

    @Column(name = "status", nullable = false, length = 32)
    private String status = STATUS_ACTIVE;

    @Column(name = "paired_at", nullable = false)
    private Instant pairedAt = Instant.now();

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected FeishuBotBindingEntity() {
    }

    public FeishuBotBindingEntity(String companyId, String userId, String tenantKey, String openId,
                                  String unionId, String chatId, String agentCode) {
        this.companyId = companyId;
        this.userId = userId;
        this.tenantKey = tenantKey;
        this.openId = openId;
        this.unionId = unionId;
        this.chatId = chatId;
        this.agentCode = agentCode == null || agentCode.isBlank() ? "cici" : agentCode;
        this.status = STATUS_ACTIVE;
        this.pairedAt = Instant.now();
        this.lastMessageAt = Instant.now();
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

    public String getTenantKey() {
        return tenantKey;
    }

    public String getOpenId() {
        return openId;
    }

    public String getUnionId() {
        return unionId;
    }

    public String getChatId() {
        return chatId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getStatus() {
        return status;
    }

    public Instant getPairedAt() {
        return pairedAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public void rebind(String userId, String unionId, String chatId, String agentCode) {
        this.userId = userId;
        this.unionId = unionId;
        this.chatId = chatId;
        this.agentCode = agentCode == null || agentCode.isBlank() ? "cici" : agentCode;
        this.status = STATUS_ACTIVE;
        this.pairedAt = Instant.now();
        this.lastMessageAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void touchProfile(String displayName, String avatarUrl) {
        this.displayName = displayName == null || displayName.isBlank() ? this.displayName : displayName.trim();
        this.avatarUrl = avatarUrl == null || avatarUrl.isBlank() ? this.avatarUrl : avatarUrl.trim();
        this.updatedAt = Instant.now();
    }

    public void touchMessage(String chatId) {
        this.chatId = chatId;
        this.lastMessageAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void unbind() {
        this.status = STATUS_UNBOUND;
        this.updatedAt = Instant.now();
    }
}
