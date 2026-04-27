package com.codehouse.ciciassistant.skill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "skill_definition")
public class SkillDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "skill_code", nullable = false, length = 64)
    private String skillCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "builtin", nullable = false)
    private boolean builtin;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "prompt_fragment", columnDefinition = "TEXT")
    private String promptFragment;

    @Column(name = "draft_spec_text", columnDefinition = "TEXT")
    private String draftSpecText;

    @Column(name = "tool_whitelist", columnDefinition = "TEXT")
    private String toolWhitelist;

    @Column(name = "kb_whitelist", columnDefinition = "TEXT")
    private String kbWhitelist;

    @Column(name = "handoff_rule", columnDefinition = "TEXT")
    private String handoffRule;

    @Column(name = "output_contract", columnDefinition = "TEXT")
    private String outputContract;

    @Column(name = "risk_level", nullable = false, length = 32)
    private String riskLevel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillDefinitionEntity() {
    }

    public SkillDefinitionEntity(String orgId,
                                 String skillCode,
                                 String name,
                                 String description,
                                 boolean builtin,
                                 boolean enabled,
                                 String promptFragment,
                                 String draftSpecText,
                                 String toolWhitelist,
                                 String kbWhitelist,
                                 String handoffRule,
                                 String outputContract,
                                 String riskLevel) {
        this.orgId = orgId;
        this.skillCode = skillCode;
        this.name = name;
        this.description = description;
        this.builtin = builtin;
        this.enabled = enabled;
        this.promptFragment = promptFragment;
        this.draftSpecText = draftSpecText;
        this.toolWhitelist = toolWhitelist;
        this.kbWhitelist = kbWhitelist;
        this.handoffRule = handoffRule;
        this.outputContract = outputContract;
        this.riskLevel = riskLevel;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getSkillCode() {
        return skillCode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getPromptFragment() {
        return promptFragment;
    }

    public String getDraftSpecText() {
        return draftSpecText;
    }

    public String getToolWhitelist() {
        return toolWhitelist;
    }

    public String getKbWhitelist() {
        return kbWhitelist;
    }

    public String getHandoffRule() {
        return handoffRule;
    }

    public String getOutputContract() {
        return outputContract;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String name,
                       String description,
                       boolean enabled,
                       String promptFragment,
                       String draftSpecText,
                       String toolWhitelist,
                       String kbWhitelist,
                       String handoffRule,
                       String outputContract,
                       String riskLevel) {
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.promptFragment = promptFragment;
        this.draftSpecText = draftSpecText;
        this.toolWhitelist = toolWhitelist;
        this.kbWhitelist = kbWhitelist;
        this.handoffRule = handoffRule;
        this.outputContract = outputContract;
        this.riskLevel = riskLevel;
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
