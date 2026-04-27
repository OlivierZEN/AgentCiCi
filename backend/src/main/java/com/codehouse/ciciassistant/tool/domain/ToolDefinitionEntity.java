package com.codehouse.ciciassistant.tool.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tool_definition")
public class ToolDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Column(name = "description", nullable = false, length = 256)
    private String description;

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    protected ToolDefinitionEntity() {
    }

    public ToolDefinitionEntity(String orgId, String toolName, String description, String riskLevel, boolean enabled) {
        this.orgId = orgId;
        this.toolName = toolName;
        this.description = description;
        this.riskLevel = riskLevel;
        this.enabled = enabled;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getDescription() {
        return description;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
