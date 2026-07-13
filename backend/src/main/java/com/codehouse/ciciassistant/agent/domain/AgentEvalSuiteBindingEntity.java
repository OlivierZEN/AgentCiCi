package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_eval_suite_binding")
public class AgentEvalSuiteBindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suite_id", nullable = false)
    private Long suiteId;

    @Column(name = "org_id", length = 64)
    private String orgId;

    @Column(name = "agent_id", length = 64)
    private String agentId;

    @Column(name = "app_code", length = 128)
    private String appCode;

    @Column(name = "industry_code", length = 128)
    private String industryCode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentEvalSuiteBindingEntity() {
    }

    public AgentEvalSuiteBindingEntity(Long suiteId,
                                       String orgId,
                                       String agentId,
                                       String appCode,
                                       String industryCode,
                                       String createdBy) {
        Instant now = Instant.now();
        this.suiteId = suiteId;
        this.orgId = orgId;
        this.agentId = agentId;
        this.appCode = appCode;
        this.industryCode = industryCode;
        this.enabled = true;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getSuiteId() { return suiteId; }
    public String getOrgId() { return orgId; }
    public String getAgentId() { return agentId; }
    public String getAppCode() { return appCode; }
    public String getIndustryCode() { return industryCode; }
    public boolean isEnabled() { return enabled; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }
}
