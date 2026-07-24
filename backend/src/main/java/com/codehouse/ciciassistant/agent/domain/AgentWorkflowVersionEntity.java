package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_workflow_version")
public class AgentWorkflowVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "version_label", length = 32)
    private String versionLabel;

    @Column(name = "spec_text", columnDefinition = "TEXT")
    private String specText;

    @Column(name = "workflow_code", columnDefinition = "TEXT")
    private String workflowCode;

    @Column(name = "workflow_manifest", columnDefinition = "TEXT")
    private String workflowManifest;

    @Column(name = "workflow_preview", columnDefinition = "TEXT")
    private String workflowPreview;

    @Column(name = "compile_summary", columnDefinition = "TEXT")
    private String compileSummary;

    @Column(name = "warnings", columnDefinition = "TEXT")
    private String warnings;

    @Column(name = "dependencies", columnDefinition = "TEXT")
    private String dependencies;

    @Column(name = "compile_fingerprint", length = 128)
    private String compileFingerprint;

    @Column(name = "change_log", columnDefinition = "TEXT")
    private String changeLog;

    @Column(name = "publish_status", nullable = false, length = 32)
    private String publishStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentWorkflowVersionEntity() {
    }

    public AgentWorkflowVersionEntity(String companyId,
                                      String agentId,
                                      Integer versionNo,
                                      String versionLabel,
                                      String specText,
                                      String workflowCode,
                                      String workflowManifest,
                                      String workflowPreview,
                                      String compileSummary,
                                      String warnings,
                                      String dependencies,
                                      String compileFingerprint,
                                      String changeLog,
                                      String publishStatus) {
        this.companyId = companyId;
        this.agentId = agentId;
        this.versionNo = versionNo;
        this.versionLabel = versionLabel;
        this.specText = specText;
        this.workflowCode = workflowCode;
        this.workflowManifest = workflowManifest;
        this.workflowPreview = workflowPreview;
        this.compileSummary = compileSummary;
        this.warnings = warnings;
        this.dependencies = dependencies;
        this.compileFingerprint = compileFingerprint;
        this.changeLog = changeLog;
        this.publishStatus = publishStatus;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getAgentId() {
        return agentId;
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

    public String getCompileFingerprint() {
        return compileFingerprint;
    }

    public String getChangeLog() {
        return changeLog;
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
