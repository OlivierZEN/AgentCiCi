package com.codehouse.ciciassistant.skill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "skill_version")
public class SkillVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "spec_text", columnDefinition = "TEXT")
    private String specText;

    @Column(name = "skill_kind", nullable = false, length = 32)
    private String skillKind;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "spec_ir_json", columnDefinition = "TEXT")
    private String specIrJson;

    @Column(name = "authoring_notes", columnDefinition = "TEXT")
    private String authoringNotes;

    @Column(name = "compiled_prompt_fragment", columnDefinition = "TEXT")
    private String compiledPromptFragment;

    @Column(name = "compiled_policy_json", columnDefinition = "TEXT")
    private String compiledPolicyJson;

    @Column(name = "effective_tool_whitelist", columnDefinition = "TEXT")
    private String effectiveToolWhitelist;

    @Column(name = "effective_kb_whitelist", columnDefinition = "TEXT")
    private String effectiveKbWhitelist;

    @Column(name = "risk_level", nullable = false, length = 32)
    private String riskLevel;

    @Column(name = "compile_summary", columnDefinition = "TEXT")
    private String compileSummary;

    @Column(name = "warnings", columnDefinition = "TEXT")
    private String warnings;

    @Column(name = "publish_status", nullable = false, length = 32)
    private String publishStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SkillVersionEntity() {
    }

    public SkillVersionEntity(String orgId,
                              Long skillId,
                              Integer versionNo,
                              String specText,
                              String skillKind,
                              String sourceType,
                              String specIrJson,
                              String authoringNotes,
                              String compiledPromptFragment,
                              String compiledPolicyJson,
                              String effectiveToolWhitelist,
                              String effectiveKbWhitelist,
                              String riskLevel,
                              String compileSummary,
                              String warnings,
                              String publishStatus) {
        this.orgId = orgId;
        this.skillId = skillId;
        this.versionNo = versionNo;
        this.specText = specText;
        this.skillKind = skillKind;
        this.sourceType = sourceType;
        this.specIrJson = specIrJson;
        this.authoringNotes = authoringNotes;
        this.compiledPromptFragment = compiledPromptFragment;
        this.compiledPolicyJson = compiledPolicyJson;
        this.effectiveToolWhitelist = effectiveToolWhitelist;
        this.effectiveKbWhitelist = effectiveKbWhitelist;
        this.riskLevel = riskLevel;
        this.compileSummary = compileSummary;
        this.warnings = warnings;
        this.publishStatus = publishStatus;
        this.createdAt = Instant.now();
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

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getSpecText() {
        return specText;
    }

    public String getSkillKind() {
        return skillKind;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSpecIrJson() {
        return specIrJson;
    }

    public String getAuthoringNotes() {
        return authoringNotes;
    }

    public String getCompiledPromptFragment() {
        return compiledPromptFragment;
    }

    public String getCompiledPolicyJson() {
        return compiledPolicyJson;
    }

    public String getEffectiveToolWhitelist() {
        return effectiveToolWhitelist;
    }

    public String getEffectiveKbWhitelist() {
        return effectiveKbWhitelist;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getCompileSummary() {
        return compileSummary;
    }

    public String getWarnings() {
        return warnings;
    }

    public String getPublishStatus() {
        return publishStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
