package com.codehouse.ciciassistant.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "platform_tool_definition")
public class PlatformToolDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "risk_level", nullable = false, length = 32)
    private String riskLevel;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlatformToolDefinitionEntity() {
    }

    public PlatformToolDefinitionEntity(String companyId,
                                        String toolName,
                                        String displayName,
                                        String description,
                                        String riskLevel,
                                        String category,
                                        boolean enabled) {
        this.companyId = companyId;
        this.toolName = toolName;
        this.displayName = displayName;
        this.description = description;
        this.riskLevel = riskLevel;
        this.category = category;
        this.enabled = enabled;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCompanyId() {
        return companyId;
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

    public String getCategory() {
        return category;
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

    public void update(String displayName,
                       String description,
                       String riskLevel,
                       String category,
                       boolean enabled) {
        this.displayName = displayName;
        this.description = description;
        this.riskLevel = riskLevel;
        this.category = category;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
