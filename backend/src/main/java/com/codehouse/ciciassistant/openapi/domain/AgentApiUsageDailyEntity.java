package com.codehouse.ciciassistant.openapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "agent_api_usage_daily")
public class AgentApiUsageDailyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "credential_id", nullable = false)
    private Long credentialId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "total_elapsed_ms", nullable = false)
    private long totalElapsedMs;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentApiUsageDailyEntity() {
    }

    public AgentApiUsageDailyEntity(String orgId, Long credentialId, LocalDate usageDate) {
        this.orgId = orgId;
        this.credentialId = credentialId;
        this.usageDate = usageDate;
        this.callCount = 0;
        this.successCount = 0;
        this.failureCount = 0;
        this.totalElapsedMs = 0;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOrgId() { return orgId; }
    public Long getCredentialId() { return credentialId; }
    public LocalDate getUsageDate() { return usageDate; }
    public int getCallCount() { return callCount; }
    public int getSuccessCount() { return successCount; }
    public int getFailureCount() { return failureCount; }
    public long getTotalElapsedMs() { return totalElapsedMs; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void reserveCall() {
        this.callCount += 1;
        this.updatedAt = Instant.now();
    }

    public void markSuccess(int elapsedMs) {
        this.successCount += 1;
        this.totalElapsedMs += Math.max(0, elapsedMs);
        this.updatedAt = Instant.now();
    }

    public void markFailure(int elapsedMs) {
        this.failureCount += 1;
        this.totalElapsedMs += Math.max(0, elapsedMs);
        this.updatedAt = Instant.now();
    }
}
