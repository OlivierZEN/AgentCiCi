package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_kb_binding")
public class AgentKnowledgeBindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentKnowledgeBindingEntity() {
    }

    public AgentKnowledgeBindingEntity(String orgId, String agentId, Long knowledgeBaseId, Integer priority, boolean enabled) {
        this.orgId = orgId;
        this.agentId = agentId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.priority = priority;
        this.enabled = enabled;
        this.createdAt = Instant.now();
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public Integer getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
