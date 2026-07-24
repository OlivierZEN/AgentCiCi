package com.codehouse.ciciassistant.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "platform_policy_bundle")
public class PlatformPolicyBundleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "bundle_code", nullable = false, length = 64)
    private String bundleCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "prompt_fragment", columnDefinition = "TEXT")
    private String promptFragment;

    @Column(name = "handoff_rules", columnDefinition = "TEXT")
    private String handoffRules;

    @Column(name = "policy_json", columnDefinition = "TEXT")
    private String policyJson;

    @Column(name = "tool_policy_json", columnDefinition = "TEXT")
    private String toolPolicyJson;

    @Column(name = "data_egress_policy_json", columnDefinition = "TEXT")
    private String dataEgressPolicyJson;

    @Column(name = "publish_status", nullable = false, length = 32)
    private String publishStatus;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlatformPolicyBundleEntity() {
    }

    public PlatformPolicyBundleEntity(String companyId,
                                      String bundleCode,
                                      String name,
                                      String description,
                                      Integer versionNo,
                                      String promptFragment,
                                      String handoffRules,
                                      String policyJson,
                                      String toolPolicyJson,
                                      String dataEgressPolicyJson,
                                      String publishStatus,
                                      String createdBy,
                                      Instant publishedAt) {
        this.companyId = companyId;
        this.bundleCode = bundleCode;
        this.name = name;
        this.description = description;
        this.versionNo = versionNo;
        this.promptFragment = promptFragment;
        this.handoffRules = handoffRules;
        this.policyJson = policyJson;
        this.toolPolicyJson = toolPolicyJson;
        this.dataEgressPolicyJson = dataEgressPolicyJson;
        this.publishStatus = publishStatus;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.publishedAt = publishedAt;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getBundleCode() {
        return bundleCode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getPromptFragment() {
        return promptFragment;
    }

    public String getHandoffRules() {
        return handoffRules;
    }

    public String getPolicyJson() {
        return policyJson;
    }

    public String getToolPolicyJson() {
        return toolPolicyJson;
    }

    public String getDataEgressPolicyJson() {
        return dataEgressPolicyJson;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markPublished() {
        this.publishStatus = "PUBLISHED";
        this.publishedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markSuperseded() {
        this.publishStatus = "SUPERSEDED";
        this.updatedAt = Instant.now();
    }
}
