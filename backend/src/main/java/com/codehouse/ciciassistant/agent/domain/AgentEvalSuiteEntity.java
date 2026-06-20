package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_eval_suite")
public class AgentEvalSuiteEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    public static final String GATE_MODE_BLOCKING = "BLOCKING";
    public static final String GATE_MODE_WARN_ONLY = "WARN_ONLY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "gate_mode", nullable = false, length = 32)
    private String gateMode;

    @Column(name = "min_pass_rate", nullable = false)
    private Double minPassRate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentEvalSuiteEntity() {
    }

    public AgentEvalSuiteEntity(String orgId,
                                String agentId,
                                String name,
                                String description,
                                String gateMode,
                                Double minPassRate) {
        Instant now = Instant.now();
        this.orgId = orgId;
        this.agentId = agentId;
        this.name = name;
        this.description = description;
        this.status = STATUS_ACTIVE;
        this.gateMode = gateMode == null || gateMode.isBlank() ? GATE_MODE_BLOCKING : gateMode;
        this.minPassRate = minPassRate == null ? 1.0d : minPassRate;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }

    public String getOrgId() { return orgId; }

    public String getAgentId() { return agentId; }

    public String getName() { return name; }

    public String getDescription() { return description; }

    public String getStatus() { return status; }

    public String getGateMode() { return gateMode; }

    public Double getMinPassRate() { return minPassRate; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
