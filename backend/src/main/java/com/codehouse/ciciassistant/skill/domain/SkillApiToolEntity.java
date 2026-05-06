package com.codehouse.ciciassistant.skill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "skill_api_tool")
public class SkillApiToolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "skill_version_id", nullable = false)
    private Long skillVersionId;

    @Column(name = "skill_code", nullable = false, length = 64)
    private String skillCode;

    @Column(name = "api_code", nullable = false, length = 64)
    private String apiCode;

    @Column(name = "tool_name", nullable = false, length = 160)
    private String toolName;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "risk_level", nullable = false, length = 32)
    private String riskLevel;

    @Column(name = "trigger_mode", nullable = false, length = 32)
    private String triggerMode;

    @Column(name = "input_schema_json", nullable = false, columnDefinition = "TEXT")
    private String inputSchemaJson;

    @Column(name = "execution_plan_json", nullable = false, columnDefinition = "TEXT")
    private String executionPlanJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SkillApiToolEntity() {
    }

    public SkillApiToolEntity(String orgId,
                              Long skillId,
                              Long skillVersionId,
                              String skillCode,
                              String apiCode,
                              String toolName,
                              String displayName,
                              String description,
                              String riskLevel,
                              String triggerMode,
                              String inputSchemaJson,
                              String executionPlanJson) {
        this.orgId = orgId;
        this.skillId = skillId;
        this.skillVersionId = skillVersionId;
        this.skillCode = skillCode;
        this.apiCode = apiCode;
        this.toolName = toolName;
        this.displayName = displayName;
        this.description = description;
        this.riskLevel = riskLevel;
        this.triggerMode = triggerMode;
        this.inputSchemaJson = inputSchemaJson;
        this.executionPlanJson = executionPlanJson;
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public Long getSkillId() {
        return skillId;
    }

    public Long getSkillVersionId() {
        return skillVersionId;
    }

    public String getSkillCode() {
        return skillCode;
    }

    public String getApiCode() {
        return apiCode;
    }

    public String getToolName() {
        return toolName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getTriggerMode() {
        return triggerMode;
    }

    public String getInputSchemaJson() {
        return inputSchemaJson;
    }

    public String getExecutionPlanJson() {
        return executionPlanJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
