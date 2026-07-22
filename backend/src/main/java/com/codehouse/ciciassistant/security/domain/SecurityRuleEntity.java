package com.codehouse.ciciassistant.security.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "security_rule")
public class SecurityRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "rule_type", nullable = false, length = 32)
    private String ruleType;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "match_type", nullable = false, length = 32)
    private String matchType;

    @Column(name = "pattern_text", nullable = false, columnDefinition = "TEXT")
    private String patternText;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "action", nullable = false, length = 16)
    private String action;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SecurityRuleEntity() {
    }

    public SecurityRuleEntity(String orgId,
                              String name,
                              String ruleType,
                              String category,
                              String matchType,
                              String patternText,
                              String severity,
                              String action,
                              boolean enabled,
                              String description) {
        this.orgId = orgId;
        this.name = name;
        this.ruleType = ruleType;
        this.category = category;
        this.matchType = matchType;
        this.patternText = patternText;
        this.severity = severity;
        this.action = action;
        this.enabled = enabled;
        this.description = description == null ? "" : description;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void update(String name,
                       String ruleType,
                       String category,
                       String matchType,
                       String patternText,
                       String severity,
                       String action,
                       boolean enabled,
                       String description) {
        this.name = name;
        this.ruleType = ruleType;
        this.category = category;
        this.matchType = matchType;
        this.patternText = patternText;
        this.severity = severity;
        this.action = action;
        this.enabled = enabled;
        this.description = description == null ? "" : description;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getName() {
        return name;
    }

    public String getRuleType() {
        return ruleType;
    }

    public String getCategory() {
        return category;
    }

    public String getMatchType() {
        return matchType;
    }

    public String getPatternText() {
        return patternText;
    }

    public String getSeverity() {
        return severity;
    }

    public String getAction() {
        return action;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
