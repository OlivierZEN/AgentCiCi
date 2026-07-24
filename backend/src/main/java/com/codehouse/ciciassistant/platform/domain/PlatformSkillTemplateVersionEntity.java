package com.codehouse.ciciassistant.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "platform_skill_template_version")
public class PlatformSkillTemplateVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "prompt_fragment", columnDefinition = "TEXT")
    private String promptFragment;

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

    @Column(name = "changelog", columnDefinition = "TEXT")
    private String changelog;

    @Column(name = "publish_status", nullable = false, length = 32)
    private String publishStatus;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "resource_uri", length = 512)
    private String resourceUri;

    @Column(name = "bundle_checksum", length = 128)
    private String bundleChecksum;

    @Column(name = "entrypoint_checksum", length = 128)
    private String entrypointChecksum;

    @Column(name = "module_manifest_json", columnDefinition = "TEXT")
    private String moduleManifestJson;

    protected PlatformSkillTemplateVersionEntity() {
    }

    public PlatformSkillTemplateVersionEntity(String companyId,
                                              String templateCode,
                                              Integer versionNo,
                                              String name,
                                              String description,
                                              String promptFragment,
                                              String toolWhitelist,
                                              String kbWhitelist,
                                              String handoffRule,
                                              String outputContract,
                                              String riskLevel,
                                              String changelog,
                                              String publishStatus,
                                              String createdBy,
                                              Instant publishedAt) {
        this.companyId = companyId;
        this.templateCode = templateCode;
        this.versionNo = versionNo;
        this.name = name;
        this.description = description;
        this.promptFragment = promptFragment;
        this.toolWhitelist = toolWhitelist;
        this.kbWhitelist = kbWhitelist;
        this.handoffRule = handoffRule;
        this.outputContract = outputContract;
        this.riskLevel = riskLevel;
        this.changelog = changelog;
        this.publishStatus = publishStatus;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.publishedAt = publishedAt;
    }

    public Long getId() {
        return id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getPromptFragment() {
        return promptFragment;
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

    public String getChangelog() {
        return changelog;
    }

    public String getPublishStatus() {
        return publishStatus;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    public String getBundleChecksum() {
        return bundleChecksum;
    }

    public String getEntrypointChecksum() {
        return entrypointChecksum;
    }

    public String getModuleManifestJson() {
        return moduleManifestJson;
    }

    public void attachFileBackedResource(String resourceUri,
                                         String bundleChecksum,
                                         String entrypointChecksum,
                                         String moduleManifestJson) {
        this.resourceUri = resourceUri;
        this.bundleChecksum = bundleChecksum;
        this.entrypointChecksum = entrypointChecksum;
        this.moduleManifestJson = moduleManifestJson;
    }

    public void markPublished() {
        this.publishStatus = "PUBLISHED";
        this.publishedAt = Instant.now();
    }

    public void markSuperseded() {
        this.publishStatus = "SUPERSEDED";
    }
}
