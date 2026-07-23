package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_task_plan")
public class AgentTaskPlanEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "org_id", nullable = false, length = 64) private String orgId;
    @Column(name = "run_id", nullable = false) private Long runId;
    @Column(name = "revision_no", nullable = false) private int revisionNo;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "goal_summary", nullable = false, length = 512) private String goalSummary;
    @Column(name = "plan_json", nullable = false, columnDefinition = "TEXT") private String planJson;
    @Column(name = "plan_hash", nullable = false, length = 64) private String planHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected AgentTaskPlanEntity() { }
    public AgentTaskPlanEntity(String orgId, long runId, int revisionNo, String goalSummary, String planJson, String planHash, Instant now) {
        this.orgId = orgId; this.runId = runId; this.revisionNo = revisionNo; this.status = "READY";
        this.goalSummary = goalSummary; this.planJson = planJson; this.planHash = planHash; this.createdAt = now;
    }
    public Long getId() { return id; }
    public String getOrgId() { return orgId; }
    public Long getRunId() { return runId; }
    public int getRevisionNo() { return revisionNo; }
    public String getStatus() { return status; }
    public String getGoalSummary() { return goalSummary; }
    public String getPlanJson() { return planJson; }
    public String getPlanHash() { return planHash; }
    public Instant getCreatedAt() { return createdAt; }
}
