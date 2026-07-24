package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_workflow_execution_log")
public class AgentWorkflowExecutionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "workflow_version_id")
    private Long workflowVersionId;

    @Column(name = "version_no")
    private Integer versionNo;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "summary", nullable = false, length = 1024)
    private String summary;

    @Column(name = "error_hint", length = 512)
    private String errorHint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentWorkflowExecutionLogEntity() {
    }

    public AgentWorkflowExecutionLogEntity(
            String companyId,
            String agentId,
            Long workflowVersionId,
            Integer versionNo,
            String source,
            String status,
            int durationMs,
            String summary,
            String errorHint,
            Instant createdAt) {
        this.companyId = companyId;
        this.agentId = agentId;
        this.workflowVersionId = workflowVersionId;
        this.versionNo = versionNo;
        this.source = source;
        this.status = status;
        this.durationMs = durationMs;
        this.summary = summary;
        this.errorHint = errorHint;
        this.createdAt = createdAt;
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

    public Long getWorkflowVersionId() {
        return workflowVersionId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getSource() {
        return source;
    }

    public String getStatus() {
        return status;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public String getSummary() {
        return summary;
    }

    public String getErrorHint() {
        return errorHint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
