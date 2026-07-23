package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "agent_task_run")
public class AgentTaskRunEntity {

    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "org_id", nullable = false, length = 64) private String orgId;
    @Column(name = "session_id", length = 128) private String sessionId;
    @Column(name = "agent_id", nullable = false, length = 64) private String agentId;
    @Column(nullable = false, length = 32) private String channel;
    @Column(nullable = false, length = 24) private String mode;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "goal_summary", nullable = false, length = 512) private String goalSummary;
    @Column(name = "max_steps", nullable = false) private int maxSteps;
    @Column(name = "current_plan_id") private Long currentPlanId;
    @Column(name = "lease_owner", length = 128) private String leaseOwner;
    @Column(name = "lease_expires_at") private Instant leaseExpiresAt;
    @Version @Column(nullable = false) private Long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "finished_at") private Instant finishedAt;

    protected AgentTaskRunEntity() { }

    public AgentTaskRunEntity(String orgId, String sessionId, String agentId, String channel,
                              String mode, String goalSummary, int maxSteps, Instant now) {
        this.orgId = orgId;
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.channel = channel;
        this.mode = mode;
        this.status = STATUS_CREATED;
        this.goalSummary = goalSummary;
        this.maxSteps = maxSteps;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void attachPlan(long planId, Instant now) { this.currentPlanId = planId; this.status = STATUS_READY; this.updatedAt = now; }
    public void claimLease(String owner, Instant expiresAt, Instant now) { this.leaseOwner = owner; this.leaseExpiresAt = expiresAt; this.status = STATUS_RUNNING; this.updatedAt = now; }
    public void releaseLease(Instant now) { this.leaseOwner = null; this.leaseExpiresAt = null; this.updatedAt = now; }
    public void succeed(Instant now) { this.status = STATUS_SUCCEEDED; this.finishedAt = now; releaseLease(now); }
    public void fail(Instant now) { this.status = STATUS_FAILED; this.finishedAt = now; releaseLease(now); }
    public boolean leaseAvailableTo(String owner, Instant now) { return leaseOwner == null || owner.equals(leaseOwner) || leaseExpiresAt == null || !leaseExpiresAt.isAfter(now); }
    public Long getId() { return id; }
    public String getOrgId() { return orgId; }
    public String getSessionId() { return sessionId; }
    public String getAgentId() { return agentId; }
    public String getChannel() { return channel; }
    public String getMode() { return mode; }
    public String getStatus() { return status; }
    public String getGoalSummary() { return goalSummary; }
    public int getMaxSteps() { return maxSteps; }
    public Long getCurrentPlanId() { return currentPlanId; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
