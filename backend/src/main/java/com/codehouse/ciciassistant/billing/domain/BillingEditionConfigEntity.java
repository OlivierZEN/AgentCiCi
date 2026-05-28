package com.codehouse.ciciassistant.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "billing_edition_config")
public class BillingEditionConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "item_type", nullable = false, length = 32)
    private String itemType;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "deployment_mode", nullable = false, length = 32)
    private String deploymentMode;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "publish_status", nullable = false, length = 32)
    private String publishStatus;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "billing_type_policy", nullable = false, length = 32)
    private String billingTypePolicy;

    @Column(name = "included_credits", nullable = false)
    private Integer includedCredits;

    @Column(name = "operation_seat_limit")
    private Integer operationSeatLimit;

    @Column(name = "builder_seat_limit")
    private Integer builderSeatLimit;

    @Column(name = "agent_limit")
    private Integer agentLimit;

    @Column(name = "skill_workflow_limit")
    private Integer skillWorkflowLimit;

    @Column(name = "knowledge_capacity_gb")
    private Integer knowledgeCapacityGb;

    @Column(name = "open_api_qps")
    private Integer openApiQps;

    @Column(name = "open_api_concurrency")
    private Integer openApiConcurrency;

    @Column(name = "open_api_credential_limit")
    private Integer openApiCredentialLimit;

    @Column(name = "connector_limit")
    private Integer connectorLimit;

    @Column(name = "meeting_concurrency")
    private Integer meetingConcurrency;

    @Column(name = "trace_retention_days")
    private Integer traceRetentionDays;

    @Column(name = "audit_retention_days")
    private Integer auditRetentionDays;

    @Column(name = "environment_limit")
    private Integer environmentLimit;

    @Column(name = "overage_mode", nullable = false, length = 32)
    private String overageMode;

    @Column(name = "sla_tier_code", length = 64)
    private String slaTierCode;

    @Column(name = "addon_category", length = 64)
    private String addonCategory;

    @Column(name = "pricing_unit", length = 64)
    private String pricingUnit;

    @Column(name = "policy_json", columnDefinition = "TEXT")
    private String policyJson;

    @Column(name = "change_reason", nullable = false, length = 1000)
    private String changeReason;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected BillingEditionConfigEntity() {
    }

    public BillingEditionConfigEntity(String orgId,
                                      String itemType,
                                      String itemCode,
                                      String displayName,
                                      String deploymentMode,
                                      Integer versionNo,
                                      String publishStatus,
                                      boolean enabled,
                                      String billingTypePolicy,
                                      Integer includedCredits,
                                      Integer operationSeatLimit,
                                      Integer builderSeatLimit,
                                      Integer agentLimit,
                                      Integer skillWorkflowLimit,
                                      Integer knowledgeCapacityGb,
                                      Integer openApiQps,
                                      Integer openApiConcurrency,
                                      Integer openApiCredentialLimit,
                                      Integer connectorLimit,
                                      Integer meetingConcurrency,
                                      Integer traceRetentionDays,
                                      Integer auditRetentionDays,
                                      Integer environmentLimit,
                                      String overageMode,
                                      String slaTierCode,
                                      String addonCategory,
                                      String pricingUnit,
                                      String policyJson,
                                      String changeReason,
                                      String createdBy,
                                      Instant publishedAt) {
        this.orgId = orgId;
        this.itemType = itemType;
        this.itemCode = itemCode;
        this.displayName = displayName;
        this.deploymentMode = deploymentMode;
        this.versionNo = versionNo;
        this.publishStatus = publishStatus;
        this.enabled = enabled;
        this.billingTypePolicy = billingTypePolicy;
        this.includedCredits = includedCredits == null ? 0 : includedCredits;
        this.operationSeatLimit = operationSeatLimit;
        this.builderSeatLimit = builderSeatLimit;
        this.agentLimit = agentLimit;
        this.skillWorkflowLimit = skillWorkflowLimit;
        this.knowledgeCapacityGb = knowledgeCapacityGb;
        this.openApiQps = openApiQps;
        this.openApiConcurrency = openApiConcurrency;
        this.openApiCredentialLimit = openApiCredentialLimit;
        this.connectorLimit = connectorLimit;
        this.meetingConcurrency = meetingConcurrency;
        this.traceRetentionDays = traceRetentionDays;
        this.auditRetentionDays = auditRetentionDays;
        this.environmentLimit = environmentLimit;
        this.overageMode = overageMode;
        this.slaTierCode = slaTierCode;
        this.addonCategory = addonCategory;
        this.pricingUnit = pricingUnit;
        this.policyJson = policyJson;
        this.changeReason = changeReason;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.publishedAt = publishedAt;
    }

    public BillingEditionConfigEntity nextDraft(Integer nextVersionNo, String reason, String actorId) {
        return new BillingEditionConfigEntity(
                orgId,
                itemType,
                itemCode,
                displayName,
                deploymentMode,
                nextVersionNo,
                "DRAFT",
                enabled,
                billingTypePolicy,
                includedCredits,
                operationSeatLimit,
                builderSeatLimit,
                agentLimit,
                skillWorkflowLimit,
                knowledgeCapacityGb,
                openApiQps,
                openApiConcurrency,
                openApiCredentialLimit,
                connectorLimit,
                meetingConcurrency,
                traceRetentionDays,
                auditRetentionDays,
                environmentLimit,
                overageMode,
                slaTierCode,
                addonCategory,
                pricingUnit,
                policyJson,
                reason,
                actorId,
                null
        );
    }

    public void replaceConfig(String displayName,
                              String deploymentMode,
                              boolean enabled,
                              String billingTypePolicy,
                              Integer includedCredits,
                              Integer operationSeatLimit,
                              Integer builderSeatLimit,
                              Integer agentLimit,
                              Integer skillWorkflowLimit,
                              Integer knowledgeCapacityGb,
                              Integer openApiQps,
                              Integer openApiConcurrency,
                              Integer openApiCredentialLimit,
                              Integer connectorLimit,
                              Integer meetingConcurrency,
                              Integer traceRetentionDays,
                              Integer auditRetentionDays,
                              Integer environmentLimit,
                              String overageMode,
                              String slaTierCode,
                              String addonCategory,
                              String pricingUnit,
                              String policyJson,
                              String changeReason) {
        this.displayName = displayName;
        this.deploymentMode = deploymentMode;
        this.enabled = enabled;
        this.billingTypePolicy = billingTypePolicy;
        this.includedCredits = includedCredits == null ? 0 : includedCredits;
        this.operationSeatLimit = operationSeatLimit;
        this.builderSeatLimit = builderSeatLimit;
        this.agentLimit = agentLimit;
        this.skillWorkflowLimit = skillWorkflowLimit;
        this.knowledgeCapacityGb = knowledgeCapacityGb;
        this.openApiQps = openApiQps;
        this.openApiConcurrency = openApiConcurrency;
        this.openApiCredentialLimit = openApiCredentialLimit;
        this.connectorLimit = connectorLimit;
        this.meetingConcurrency = meetingConcurrency;
        this.traceRetentionDays = traceRetentionDays;
        this.auditRetentionDays = auditRetentionDays;
        this.environmentLimit = environmentLimit;
        this.overageMode = overageMode;
        this.slaTierCode = slaTierCode;
        this.addonCategory = addonCategory;
        this.pricingUnit = pricingUnit;
        this.policyJson = policyJson;
        this.changeReason = changeReason;
        this.updatedAt = Instant.now();
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

    public void setEnabled(boolean enabled, String changeReason) {
        this.enabled = enabled;
        this.changeReason = changeReason;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getItemType() {
        return itemType;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDeploymentMode() {
        return deploymentMode;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getPublishStatus() {
        return publishStatus;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBillingTypePolicy() {
        return billingTypePolicy;
    }

    public Integer getIncludedCredits() {
        return includedCredits;
    }

    public Integer getOperationSeatLimit() {
        return operationSeatLimit;
    }

    public Integer getBuilderSeatLimit() {
        return builderSeatLimit;
    }

    public Integer getAgentLimit() {
        return agentLimit;
    }

    public Integer getSkillWorkflowLimit() {
        return skillWorkflowLimit;
    }

    public Integer getKnowledgeCapacityGb() {
        return knowledgeCapacityGb;
    }

    public Integer getOpenApiQps() {
        return openApiQps;
    }

    public Integer getOpenApiConcurrency() {
        return openApiConcurrency;
    }

    public Integer getOpenApiCredentialLimit() {
        return openApiCredentialLimit;
    }

    public Integer getConnectorLimit() {
        return connectorLimit;
    }

    public Integer getMeetingConcurrency() {
        return meetingConcurrency;
    }

    public Integer getTraceRetentionDays() {
        return traceRetentionDays;
    }

    public Integer getAuditRetentionDays() {
        return auditRetentionDays;
    }

    public Integer getEnvironmentLimit() {
        return environmentLimit;
    }

    public String getOverageMode() {
        return overageMode;
    }

    public String getSlaTierCode() {
        return slaTierCode;
    }

    public String getAddonCategory() {
        return addonCategory;
    }

    public String getPricingUnit() {
        return pricingUnit;
    }

    public String getPolicyJson() {
        return policyJson;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
