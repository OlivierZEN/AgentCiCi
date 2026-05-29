package com.codehouse.ciciassistant.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "billing_edition")
public class BillingEditionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_mode", nullable = false, length = 32)
    private String deploymentMode;

    @Column(name = "edition_code", nullable = false, unique = true, length = 64)
    private String editionCode;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "description", nullable = false, length = 1000)
    private String description = "";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "operation_seat_limit")
    private Integer operationSeatLimit;

    @Column(name = "builder_seat_limit")
    private Integer builderSeatLimit;

    @Column(name = "agent_limit")
    private Integer agentLimit;

    @Column(name = "skill_limit")
    private Integer skillLimit;

    @Column(name = "workflow_limit")
    private Integer workflowLimit;

    @Column(name = "knowledge_base_limit")
    private Integer knowledgeBaseLimit;

    @Column(name = "document_limit")
    private Integer documentLimit;

    @Column(name = "chunk_limit")
    private Integer chunkLimit;

    @Column(name = "knowledge_storage_mb")
    private Integer knowledgeStorageMb;

    @Column(name = "open_api_qps")
    private Integer openApiQps;

    @Column(name = "open_api_concurrency")
    private Integer openApiConcurrency;

    @Column(name = "open_api_credential_limit")
    private Integer openApiCredentialLimit;

    @Column(name = "connector_limit")
    private Integer connectorLimit;

    @Column(name = "meeting_minutes_concurrency")
    private Integer meetingMinutesConcurrency;

    @Column(name = "trace_retention_days")
    private Integer traceRetentionDays;

    @Column(name = "audit_retention_days")
    private Integer auditRetentionDays;

    @Column(name = "environment_limit")
    private Integer environmentLimit;

    @Column(name = "included_credits", nullable = false, precision = 18, scale = 2)
    private BigDecimal includedCredits = BigDecimal.ZERO;

    @Column(name = "overage_mode", nullable = false, length = 32)
    private String overageMode = "soft_limit";

    @Column(name = "billing_type_policy", nullable = false, length = 32)
    private String billingTypePolicy = "included";

    @Column(name = "sla_tier_code", nullable = false, length = 64)
    private String slaTierCode = "standard";

    @Column(name = "top_up_policy", nullable = false, length = 64)
    private String topUpPolicy = "disabled";

    @Column(name = "local_model_token_policy", nullable = false, length = 1000)
    private String localModelTokenPolicy = "";

    @Column(name = "platform_paid_resource_policy", nullable = false, length = 1000)
    private String platformPaidResourcePolicy = "";

    @Column(name = "package_codes", nullable = false, columnDefinition = "TEXT")
    private String packageCodes = "[]";

    @Column(name = "version_no", nullable = false)
    private int versionNo = 1;

    @Column(name = "change_reason", nullable = false, length = 1000)
    private String changeReason = "initial seed";

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy = "system";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected BillingEditionEntity() {
    }

    public BillingEditionEntity(String deploymentMode, String editionCode, String displayName, int sortOrder) {
        this.deploymentMode = deploymentMode;
        this.editionCode = editionCode;
        this.displayName = displayName;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getDeploymentMode() {
        return deploymentMode;
    }

    public void setDeploymentMode(String deploymentMode) {
        this.deploymentMode = deploymentMode;
    }

    public String getEditionCode() {
        return editionCode;
    }

    public void setEditionCode(String editionCode) {
        this.editionCode = editionCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getOperationSeatLimit() {
        return operationSeatLimit;
    }

    public void setOperationSeatLimit(Integer operationSeatLimit) {
        this.operationSeatLimit = operationSeatLimit;
    }

    public Integer getBuilderSeatLimit() {
        return builderSeatLimit;
    }

    public void setBuilderSeatLimit(Integer builderSeatLimit) {
        this.builderSeatLimit = builderSeatLimit;
    }

    public Integer getAgentLimit() {
        return agentLimit;
    }

    public void setAgentLimit(Integer agentLimit) {
        this.agentLimit = agentLimit;
    }

    public Integer getSkillLimit() {
        return skillLimit;
    }

    public void setSkillLimit(Integer skillLimit) {
        this.skillLimit = skillLimit;
    }

    public Integer getWorkflowLimit() {
        return workflowLimit;
    }

    public void setWorkflowLimit(Integer workflowLimit) {
        this.workflowLimit = workflowLimit;
    }

    public Integer getKnowledgeBaseLimit() {
        return knowledgeBaseLimit;
    }

    public void setKnowledgeBaseLimit(Integer knowledgeBaseLimit) {
        this.knowledgeBaseLimit = knowledgeBaseLimit;
    }

    public Integer getDocumentLimit() {
        return documentLimit;
    }

    public void setDocumentLimit(Integer documentLimit) {
        this.documentLimit = documentLimit;
    }

    public Integer getChunkLimit() {
        return chunkLimit;
    }

    public void setChunkLimit(Integer chunkLimit) {
        this.chunkLimit = chunkLimit;
    }

    public Integer getKnowledgeStorageMb() {
        return knowledgeStorageMb;
    }

    public void setKnowledgeStorageMb(Integer knowledgeStorageMb) {
        this.knowledgeStorageMb = knowledgeStorageMb;
    }

    public Integer getOpenApiQps() {
        return openApiQps;
    }

    public void setOpenApiQps(Integer openApiQps) {
        this.openApiQps = openApiQps;
    }

    public Integer getOpenApiConcurrency() {
        return openApiConcurrency;
    }

    public void setOpenApiConcurrency(Integer openApiConcurrency) {
        this.openApiConcurrency = openApiConcurrency;
    }

    public Integer getOpenApiCredentialLimit() {
        return openApiCredentialLimit;
    }

    public void setOpenApiCredentialLimit(Integer openApiCredentialLimit) {
        this.openApiCredentialLimit = openApiCredentialLimit;
    }

    public Integer getConnectorLimit() {
        return connectorLimit;
    }

    public void setConnectorLimit(Integer connectorLimit) {
        this.connectorLimit = connectorLimit;
    }

    public Integer getMeetingMinutesConcurrency() {
        return meetingMinutesConcurrency;
    }

    public void setMeetingMinutesConcurrency(Integer meetingMinutesConcurrency) {
        this.meetingMinutesConcurrency = meetingMinutesConcurrency;
    }

    public Integer getTraceRetentionDays() {
        return traceRetentionDays;
    }

    public void setTraceRetentionDays(Integer traceRetentionDays) {
        this.traceRetentionDays = traceRetentionDays;
    }

    public Integer getAuditRetentionDays() {
        return auditRetentionDays;
    }

    public void setAuditRetentionDays(Integer auditRetentionDays) {
        this.auditRetentionDays = auditRetentionDays;
    }

    public Integer getEnvironmentLimit() {
        return environmentLimit;
    }

    public void setEnvironmentLimit(Integer environmentLimit) {
        this.environmentLimit = environmentLimit;
    }

    public BigDecimal getIncludedCredits() {
        return includedCredits;
    }

    public void setIncludedCredits(BigDecimal includedCredits) {
        this.includedCredits = includedCredits;
    }

    public String getOverageMode() {
        return overageMode;
    }

    public void setOverageMode(String overageMode) {
        this.overageMode = overageMode;
    }

    public String getBillingTypePolicy() {
        return billingTypePolicy;
    }

    public void setBillingTypePolicy(String billingTypePolicy) {
        this.billingTypePolicy = billingTypePolicy;
    }

    public String getSlaTierCode() {
        return slaTierCode;
    }

    public void setSlaTierCode(String slaTierCode) {
        this.slaTierCode = slaTierCode;
    }

    public String getTopUpPolicy() {
        return topUpPolicy;
    }

    public void setTopUpPolicy(String topUpPolicy) {
        this.topUpPolicy = topUpPolicy;
    }

    public String getLocalModelTokenPolicy() {
        return localModelTokenPolicy;
    }

    public void setLocalModelTokenPolicy(String localModelTokenPolicy) {
        this.localModelTokenPolicy = localModelTokenPolicy;
    }

    public String getPlatformPaidResourcePolicy() {
        return platformPaidResourcePolicy;
    }

    public void setPlatformPaidResourcePolicy(String platformPaidResourcePolicy) {
        this.platformPaidResourcePolicy = platformPaidResourcePolicy;
    }

    public String getPackageCodes() {
        return packageCodes;
    }

    public void setPackageCodes(String packageCodes) {
        this.packageCodes = packageCodes;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
