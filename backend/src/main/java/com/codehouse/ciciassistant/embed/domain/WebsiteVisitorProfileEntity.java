package com.codehouse.ciciassistant.embed.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "website_visitor_profile")
public class WebsiteVisitorProfileEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "external_tenant_id", nullable = false, length = 128)
    private String externalTenantId;

    @Column(name = "external_user_id", nullable = false, length = 128)
    private String externalUserId;

    @Column(name = "last_summary", columnDefinition = "TEXT")
    private String lastSummary;

    @Column(name = "has_lead", nullable = false)
    private boolean hasLead;

    @Column(name = "last_visit_at")
    private Instant lastVisitAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebsiteVisitorProfileEntity() {
    }

    public WebsiteVisitorProfileEntity(String companyId, String agentId, String externalTenantId, String externalUserId) {
        this.id = UUID.randomUUID().toString();
        this.companyId = companyId;
        this.agentId = agentId;
        this.externalTenantId = externalTenantId;
        this.externalUserId = externalUserId;
        this.hasLead = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getAgentId() { return agentId; }
    public String getExternalTenantId() { return externalTenantId; }
    public String getExternalUserId() { return externalUserId; }
    public String getLastSummary() { return lastSummary; }
    public boolean isHasLead() { return hasLead; }
    public Instant getLastVisitAt() { return lastVisitAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void recordVisit(Instant at) {
        this.lastVisitAt = at;
        this.updatedAt = at;
    }

    public void recordSummary(String summary) {
        this.lastSummary = summary == null || summary.isBlank() ? null : summary.trim();
        this.updatedAt = Instant.now();
    }

    public void markLeadCaptured() {
        this.hasLead = true;
        this.updatedAt = Instant.now();
    }
}
