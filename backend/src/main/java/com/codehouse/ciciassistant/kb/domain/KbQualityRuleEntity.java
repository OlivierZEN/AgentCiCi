package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_quality_rule")
public class KbQualityRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "rule_type", nullable = false, length = 32)
    private String ruleType;

    @Column(name = "pattern")
    private String pattern;

    @Column(name = "replacement")
    private String replacement;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KbQualityRuleEntity() {
    }

    public KbQualityRuleEntity(String orgId, Long knowledgeBaseId, String name, String ruleType,
                               String pattern, String replacement, boolean enabled, String createdBy) {
        this.orgId = orgId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.name = name;
        this.ruleType = ruleType;
        this.pattern = pattern;
        this.replacement = replacement;
        this.enabled = enabled;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public String getOrgId() { return orgId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public String getName() { return name; }
    public String getRuleType() { return ruleType; }
    public String getPattern() { return pattern; }
    public String getReplacement() { return replacement; }
    public boolean isEnabled() { return enabled; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String name, String ruleType, String pattern, String replacement, boolean enabled) {
        this.name = name;
        this.ruleType = ruleType;
        this.pattern = pattern;
        this.replacement = replacement;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
