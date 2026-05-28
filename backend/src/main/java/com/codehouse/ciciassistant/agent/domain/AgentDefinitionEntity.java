package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_definition")
public class AgentDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "summary", length = 512)
    private String summary;

    @Column(name = "greeting", columnDefinition = "TEXT")
    private String greeting;

    @Column(name = "model", nullable = false, length = 128)
    private String model;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "handoff_rule", columnDefinition = "TEXT")
    private String handoffRule;

    @Column(name = "safety_level", nullable = false, length = 32)
    private String safetyLevel;

    @Column(name = "execution_mode", nullable = false, length = 32)
    private String executionMode;

    @Column(name = "version_label", length = 32)
    private String versionLabel;

    @Column(name = "avatar_base64", columnDefinition = "TEXT")
    private String avatarBase64;

    @Column(name = "owner_user_id", length = 64)
    private String ownerUserId;

    @Column(name = "builtin", nullable = false)
    private boolean builtin;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "published_version_id")
    private Long publishedVersionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentDefinitionEntity() {
    }

    public AgentDefinitionEntity(String orgId,
                                 String agentId,
                                 String name,
                                 String summary,
                                 String greeting,
                                 String model,
                                 String systemPrompt,
                                 String handoffRule,
                                 String safetyLevel,
                                 String executionMode,
                                 String versionLabel,
                                 String avatarBase64,
                                 boolean builtin,
                                 boolean enabled) {
        this(orgId, agentId, name, summary, greeting, model, systemPrompt, handoffRule, safetyLevel, executionMode,
                versionLabel, avatarBase64, null, builtin, enabled);
    }

    public AgentDefinitionEntity(String orgId,
                                 String agentId,
                                 String name,
                                 String summary,
                                 String greeting,
                                 String model,
                                 String systemPrompt,
                                 String handoffRule,
                                 String safetyLevel,
                                 String executionMode,
                                 String versionLabel,
                                 String avatarBase64,
                                 String ownerUserId,
                                 boolean builtin,
                                 boolean enabled) {
        this.orgId = orgId;
        this.agentId = agentId;
        this.name = name;
        this.summary = summary;
        this.greeting = greeting;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.handoffRule = handoffRule;
        this.safetyLevel = safetyLevel;
        this.executionMode = executionMode;
        this.versionLabel = versionLabel;
        this.avatarBase64 = avatarBase64;
        this.ownerUserId = ownerUserId;
        this.builtin = builtin;
        this.enabled = enabled;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
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

    public String getName() {
        return name;
    }

    public String getSummary() {
        return summary;
    }

    public String getGreeting() {
        return greeting;
    }

    public String getModel() {
        return model;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getHandoffRule() {
        return handoffRule;
    }

    public String getSafetyLevel() {
        return safetyLevel;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public String getAvatarBase64() {
        return avatarBase64;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Long getPublishedVersionId() {
        return publishedVersionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setPublishedVersionId(Long publishedVersionId) {
        this.publishedVersionId = publishedVersionId;
        this.updatedAt = Instant.now();
    }

    public void markDeleted() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    public void update(String name,
                       String summary,
                       String greeting,
                       String model,
                       String systemPrompt,
                       String handoffRule,
                       String safetyLevel,
                       String executionMode,
                       String versionLabel,
                       String avatarBase64,
                       boolean replaceAvatarBase64,
                       boolean enabled) {
        this.name = name;
        this.summary = summary;
        this.greeting = greeting;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.handoffRule = handoffRule;
        this.safetyLevel = safetyLevel;
        this.executionMode = executionMode;
        this.versionLabel = versionLabel;
        if (replaceAvatarBase64) {
            this.avatarBase64 = avatarBase64;
        }
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
