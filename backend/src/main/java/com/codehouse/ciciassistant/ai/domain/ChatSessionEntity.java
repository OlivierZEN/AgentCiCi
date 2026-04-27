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

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "agent_id", length = 64)
    private String agentId;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChatSessionEntity() {
    }

    public ChatSessionEntity(String id, String orgId, String userId, String agentId, String title) {
        this.id = id;
        this.orgId = orgId;
        this.userId = userId;
        this.agentId = agentId;
        this.title = title;
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
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

    public void touch(String title, String agentId) {
        this.title = title;
        if (agentId != null && !agentId.isBlank()) {
            this.agentId = agentId;
        }
        this.updatedAt = Instant.now();
    }
}
