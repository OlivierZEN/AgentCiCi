package com.codehouse.ciciassistant.userworkflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_workflow_version")
public class UserWorkflowVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "spec_id", nullable = false)
    private Long specId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "version_label", length = 64)
    private String versionLabel;

    @Column(name = "spec_text", nullable = false, columnDefinition = "TEXT")
    private String specText;

    @Column(name = "workflow_code", nullable = false, columnDefinition = "TEXT")
    private String workflowCode;

    @Column(name = "workflow_manifest", nullable = false, columnDefinition = "TEXT")
    private String workflowManifest;

    @Column(name = "workflow_preview", nullable = false, columnDefinition = "TEXT")
    private String workflowPreview;

    @Column(name = "compile_summary", nullable = false, columnDefinition = "TEXT")
    private String compileSummary;

    @Column(name = "warnings", nullable = false, columnDefinition = "TEXT")
    private String warnings;

    @Column(name = "dependencies", nullable = false, columnDefinition = "TEXT")
    private String dependencies;

    @Column(name = "publish_status", nullable = false, length = 32)
    private String publishStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserWorkflowVersionEntity() {
    }

    public UserWorkflowVersionEntity(String companyId,
                                     String userId,
                                     String agentId,
                                     Long specId,
                                     Integer versionNo,
                                     String versionLabel,
                                     String specText,
                                     String workflowCode,
                                     String workflowManifest,
                                     String workflowPreview,
                                     String compileSummary,
                                     String warnings,
                                     String dependencies,
                                     String publishStatus) {
        this.companyId = companyId;
        this.userId = userId;
        this.agentId = agentId;
        this.specId = specId;
        this.versionNo = versionNo;
        this.versionLabel = versionLabel;
        this.specText = specText;
        this.workflowCode = workflowCode;
        this.workflowManifest = workflowManifest;
        this.workflowPreview = workflowPreview;
        this.compileSummary = compileSummary;
        this.warnings = warnings;
        this.dependencies = dependencies;
        this.publishStatus = publishStatus;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getUserId() {
        return userId;
    }

    public String getAgentId() {
        return agentId;
    }

    public Long getSpecId() {
        return specId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public String getSpecText() {
        return specText;
    }

    public String getWorkflowCode() {
        return workflowCode;
    }

    public String getWorkflowManifest() {
        return workflowManifest;
    }

    public String getWorkflowPreview() {
        return workflowPreview;
    }

    public String getCompileSummary() {
        return compileSummary;
    }

    public String getWarnings() {
        return warnings;
    }

    public String getDependencies() {
        return dependencies;
    }

    public String getPublishStatus() {
        return publishStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setPublishStatus(String publishStatus) {
        this.publishStatus = publishStatus;
    }
}
