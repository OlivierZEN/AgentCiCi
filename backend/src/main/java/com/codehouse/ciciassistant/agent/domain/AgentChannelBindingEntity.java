package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_channel_binding")
public class AgentChannelBindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "channel_id", nullable = false, length = 32)
    private String channelId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentChannelBindingEntity() {
    }

    public AgentChannelBindingEntity(String companyId, String agentId, String channelId, boolean enabled) {
        this.companyId = companyId;
        this.agentId = agentId;
        this.channelId = channelId;
        this.enabled = enabled;
        this.createdAt = Instant.now();
    }

    public String getChannelId() {
        return channelId;
    }

    public String getAgentId() {
        return agentId;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
