package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_runtime_schedule_trigger")
public class AgentRuntimeScheduleTriggerEntity {

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

    @Column(name = "trigger_key", nullable = false, length = 128)
    private String triggerKey;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "cadence", length = 64)
    private String cadence;

    @Column(name = "detail", length = 1024)
    private String detail;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "stub", nullable = false)
    private boolean stub;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentRuntimeScheduleTriggerEntity() {
    }

    public AgentRuntimeScheduleTriggerEntity(String companyId,
                                             String agentId,
                                             Long workflowVersionId,
                                             Integer versionNo,
                                             String triggerKey,
                                             String title,
                                             String cadence,
                                             String detail,
                                             String source,
                                             boolean stub) {
        this.companyId = companyId;
        this.agentId = agentId;
        this.workflowVersionId = workflowVersionId;
        this.versionNo = versionNo;
        this.triggerKey = triggerKey;
        this.title = title;
        this.cadence = cadence;
        this.detail = detail;
        this.source = source;
        this.stub = stub;
        this.active = true;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
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

    public String getTriggerKey() {
        return triggerKey;
    }

    public String getTitle() {
        return title;
    }

    public String getCadence() {
        return cadence;
    }

    public String getDetail() {
        return detail;
    }

    public String getSource() {
        return source;
    }

    public boolean isStub() {
        return stub;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void updateEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
