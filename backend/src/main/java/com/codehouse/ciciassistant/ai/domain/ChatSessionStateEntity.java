package com.codehouse.ciciassistant.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "chat_session_state")
public class ChatSessionStateEntity {

    @Id
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "agent_id", length = 64)
    private String agentId;

    @Column(name = "summary", nullable = false, length = 512)
    private String summary;

    @Column(name = "state_json", nullable = false, columnDefinition = "TEXT")
    private String stateJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ChatSessionStateEntity() {
    }

    public ChatSessionStateEntity(String sessionId, String orgId, String agentId, String summary,
                                  String stateJson, Instant updatedAt) {
        this.sessionId = sessionId;
        this.orgId = orgId;
        this.agentId = agentId;
        this.summary = summary;
        this.stateJson = stateJson;
        this.updatedAt = updatedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getSummary() {
        return summary;
    }

    public String getStateJson() {
        return stateJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setStateJson(String stateJson) {
        this.stateJson = stateJson;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
