package com.codehouse.ciciassistant.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_session")
public class ChatSessionEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "agent_id", length = 64)
    private String agentId;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "channel_code", nullable = false, length = 32)
    private String channelCode;

    @Column(name = "visibility_scope", nullable = false, length = 16)
    private String visibilityScope;

    @Column(name = "source_key", length = 160)
    private String sourceKey;

    protected ChatSessionEntity() {
    }

    public ChatSessionEntity(String id, String companyId, String userId, String agentId, String title) {
        this(id, companyId, userId, agentId, title, "web", "USER", null);
    }

    public ChatSessionEntity(String id,
                             String companyId,
                             String userId,
                             String agentId,
                             String title,
                             String channelCode,
                             String visibilityScope,
                             String sourceKey) {
        this.id = id;
        this.companyId = companyId;
        this.userId = userId;
        this.agentId = agentId;
        this.title = title;
        this.channelCode = channelCode;
        this.visibilityScope = visibilityScope;
        this.sourceKey = sourceKey;
        this.updatedAt = Instant.now();
    }

    public String getId() {
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

    public String getTitle() {
        return title;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public String getVisibilityScope() {
        return visibilityScope;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public boolean isCompanyVisible() {
        return "COMPANY".equals(visibilityScope);
    }

    public String routingKey() {
        return sourceKey == null || sourceKey.isBlank() ? id : sourceKey;
    }

    public void touch(String title, String agentId) {
        this.title = title;
        if (agentId != null && !agentId.isBlank()) {
            this.agentId = agentId;
        }
        this.updatedAt = Instant.now();
    }
}
