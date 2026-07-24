package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_tool_binding")
public class AgentToolBindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "tool_id", nullable = false, length = 128)
    private String toolId;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentToolBindingEntity() {
    }

    public AgentToolBindingEntity(String companyId, String agentId, String toolId, Integer priority, boolean enabled) {
        this.companyId = companyId;
        this.agentId = agentId;
        this.toolId = toolId;
        this.priority = priority;
        this.enabled = enabled;
        this.createdAt = Instant.now();
    }

    public String getToolId() {
        return toolId;
    }

    public Integer getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
