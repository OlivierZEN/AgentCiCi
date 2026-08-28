package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_publish_config")
public class AgentPublishConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "channel_id", nullable = false, length = 32)
    private String channelId;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentPublishConfigEntity() {
    }

    public AgentPublishConfigEntity(String companyId, String agentId, String channelId, String configJson) {
        this.companyId = companyId;
        this.agentId = agentId;
        this.channelId = channelId;
        this.configJson = configJson;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getChannelId() {
        return channelId;
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getConfigJson() {
        return configJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateConfigJson(String configJson) {
        this.configJson = configJson;
        this.updatedAt = Instant.now();
    }
}
