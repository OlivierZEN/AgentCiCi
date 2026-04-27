package com.codehouse.ciciassistant.skill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_skill_binding")
public class AgentSkillBindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "activation_mode", nullable = false, length = 32)
    private String activationMode;

    @Column(name = "activation_condition", columnDefinition = "TEXT")
    private String activationCondition;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentSkillBindingEntity() {
    }

    public AgentSkillBindingEntity(String orgId,
                                   String agentId,
                                   Long skillId,
                                   String activationMode,
                                   String activationCondition,
                                   Integer priority,
                                   boolean enabled) {
        this.orgId = orgId;
        this.agentId = agentId;
        this.skillId = skillId;
        this.activationMode = activationMode;
        this.activationCondition = activationCondition;
        this.priority = priority;
        this.enabled = enabled;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getAgentId() {
        return agentId;
    }

    public Long getSkillId() {
        return skillId;
    }

    public String getActivationMode() {
        return activationMode;
    }

    public String getActivationCondition() {
        return activationCondition;
    }

    public Integer getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
