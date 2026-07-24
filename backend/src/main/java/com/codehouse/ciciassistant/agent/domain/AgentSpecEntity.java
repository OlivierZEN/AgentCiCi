package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_spec")
public class AgentSpecEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "spec_text", columnDefinition = "TEXT")
    private String specText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentSpecEntity() {
    }

    public AgentSpecEntity(String companyId, String agentId, String specText) {
        this.companyId = companyId;
        this.agentId = agentId;
        this.specText = specText;
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

    public String getSpecText() {
        return specText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateSpecText(String specText) {
        this.specText = specText;
        this.updatedAt = Instant.now();
    }
}
