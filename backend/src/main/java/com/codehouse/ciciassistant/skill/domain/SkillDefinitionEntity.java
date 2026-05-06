package com.codehouse.ciciassistant.skill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Column(name = "runtime_api_draft_json", columnDefinition = "TEXT")
    private String runtimeApiDraftJson;

    @Column(name = "risk_level", nullable = false, length = 32)
    private String riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private SkillSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private SkillVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "edit_policy", nullable = false, length = 32)
    private SkillEditPolicy editPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "binding_policy", nullable = false, length = 32)
    private SkillBindingPolicy bindingPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "update_policy", nullable = false, length = 32)
    private SkillUpdatePolicy updatePolicy;

    @Column(name = "template_code", length = 64)
    private String templateCode;

    @Column(name = "base_template_version")
    private Integer baseTemplateVersion;

    @Column(name = "current_published_version_id")
    private Long currentPublishedVersionId;

    @Column(name = "latest_draft_version_id")
    private Long latestDraftVersionId;

    @Column(name = "lifecycle_status", length = 32)
    private String lifecycleStatus;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 128)
    private String deletedBy;

    @Column(name = "delete_reason", columnDefinition = "TEXT")
    private String deleteReason;

    @Column(name = "last_published_at")
    private Instant lastPublishedAt;

    @Column(name = "last_published_by", length = 128)
    private String lastPublishedBy;

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
                                 String riskLevel,
                                 SkillSourceType sourceType,
                                 SkillVisibility visibility,
                                 SkillEditPolicy editPolicy,
                                 SkillBindingPolicy bindingPolicy,
                                 SkillUpdatePolicy updatePolicy,
                                 String templateCode,
                                 Integer baseTemplateVersion) {
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
        this.sourceType = sourceType;
        this.visibility = visibility;
        this.editPolicy = editPolicy;
        this.bindingPolicy = bindingPolicy;
        this.updatePolicy = updatePolicy;
        this.templateCode = templateCode;
        this.baseTemplateVersion = baseTemplateVersion;
        this.lifecycleStatus = "DRAFT";
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

    public String getRuntimeApiDraftJson() {
        return runtimeApiDraftJson;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public SkillSourceType getSourceType() {
        return sourceType;
    }

    public SkillVisibility getVisibility() {
        return visibility;
    }

    public SkillEditPolicy getEditPolicy() {
        return editPolicy;
    }

    public SkillBindingPolicy getBindingPolicy() {
        return bindingPolicy;
    }

    public SkillUpdatePolicy getUpdatePolicy() {
        return updatePolicy;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public Integer getBaseTemplateVersion() {
        return baseTemplateVersion;
    }

    public Long getCurrentPublishedVersionId() {
        return currentPublishedVersionId;
    }

    public Long getLatestDraftVersionId() {
        return latestDraftVersionId;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus == null ? (enabled ? "DRAFT" : "DISABLED") : lifecycleStatus;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public String getDeleteReason() {
        return deleteReason;
    }

    public Instant getLastPublishedAt() {
        return lastPublishedAt;
    }

    public String getLastPublishedBy() {
        return lastPublishedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String skillCode,
                       String name,
                       String description,
                       boolean enabled,
                       String promptFragment,
                       String draftSpecText,
                       String toolWhitelist,
                       String kbWhitelist,
                       String handoffRule,
                       String outputContract,
                       String runtimeApiDraftJson,
                       String riskLevel) {
        this.skillCode = skillCode;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.promptFragment = promptFragment;
        this.draftSpecText = draftSpecText;
        this.toolWhitelist = toolWhitelist;
        this.kbWhitelist = kbWhitelist;
        this.handoffRule = handoffRule;
        this.outputContract = outputContract;
        this.runtimeApiDraftJson = runtimeApiDraftJson;
        this.riskLevel = riskLevel;
        this.updatedAt = Instant.now();
    }

    public void setRuntimeApiDraftJson(String runtimeApiDraftJson) {
        this.runtimeApiDraftJson = runtimeApiDraftJson;
        this.updatedAt = Instant.now();
    }

    public boolean isVisibleToTenant() {
        return visibility == SkillVisibility.VISIBLE && !"DELETED".equals(getLifecycleStatus());
    }

    public boolean isTenantEditable() {
        return editPolicy == SkillEditPolicy.EDITABLE;
    }

    public boolean isTenantConfigurable() {
        return editPolicy == SkillEditPolicy.CONFIGURABLE;
    }

    public boolean isTenantDeletable() {
        return sourceType == SkillSourceType.TENANT_CUSTOM && editPolicy == SkillEditPolicy.EDITABLE;
    }

    public boolean isInternalOnly() {
        return bindingPolicy == SkillBindingPolicy.INTERNAL_ONLY;
    }

    public boolean isMandatoryBinding() {
        return bindingPolicy == SkillBindingPolicy.MANDATORY;
    }

    public boolean isPlatformCorePolicyCandidate() {
        return sourceType == SkillSourceType.PLATFORM_STANDARD
                && visibility == SkillVisibility.HIDDEN
                && bindingPolicy == SkillBindingPolicy.MANDATORY;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!"DELETED".equals(getLifecycleStatus())) {
            this.lifecycleStatus = enabled ? fallbackLifecycleStatus() : "DISABLED";
        }
        this.updatedAt = Instant.now();
    }

    public void setLatestDraftVersionId(Long latestDraftVersionId) {
        this.latestDraftVersionId = latestDraftVersionId;
        this.updatedAt = Instant.now();
    }

    public void setCurrentPublishedVersionId(Long currentPublishedVersionId) {
        this.currentPublishedVersionId = currentPublishedVersionId;
        this.lifecycleStatus = "PUBLISHED";
        this.lastPublishedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markPublished(Long currentPublishedVersionId, String publishedBy) {
        this.currentPublishedVersionId = currentPublishedVersionId;
        this.lifecycleStatus = "PUBLISHED";
        this.lastPublishedAt = Instant.now();
        this.lastPublishedBy = publishedBy;
        this.updatedAt = Instant.now();
    }

    public void markDraft(Long latestDraftVersionId) {
        this.latestDraftVersionId = latestDraftVersionId;
        if (!"DELETED".equals(getLifecycleStatus()) && currentPublishedVersionId == null) {
            this.lifecycleStatus = "DRAFT";
        }
        this.updatedAt = Instant.now();
    }

    public void markDeleted(String deletedBy, String reason) {
        this.enabled = false;
        this.visibility = SkillVisibility.HIDDEN;
        this.lifecycleStatus = "DELETED";
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
        this.deleteReason = reason;
        archiveDeletedSkillCode();
        this.updatedAt = Instant.now();
    }

    public void archiveDeletedSkillCode() {
        if (!"DELETED".equals(getLifecycleStatus()) || id == null) {
            return;
        }
        String suffix = "__deleted_" + id;
        if (skillCode != null && skillCode.endsWith(suffix)) {
            return;
        }
        String base = skillCode == null || skillCode.isBlank() ? "skill" : skillCode;
        int maxBaseLength = Math.max(1, 64 - suffix.length());
        this.skillCode = base.substring(0, Math.min(base.length(), maxBaseLength)) + suffix;
    }

    public void setVisibility(SkillVisibility visibility) {
        this.visibility = visibility;
        this.updatedAt = Instant.now();
    }

    public void setBindingPolicy(SkillBindingPolicy bindingPolicy) {
        this.bindingPolicy = bindingPolicy;
        this.updatedAt = Instant.now();
    }

    public void setUpdatePolicy(SkillUpdatePolicy updatePolicy) {
        this.updatePolicy = updatePolicy;
        this.updatedAt = Instant.now();
    }

    private String fallbackLifecycleStatus() {
        return currentPublishedVersionId == null ? "DRAFT" : "PUBLISHED";
    }
}
