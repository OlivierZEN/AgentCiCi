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
@Table(name = "agent_task_step")
public class AgentTaskStepEntity {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "org_id", nullable = false, length = 64) private String orgId;
    @Column(name = "run_id", nullable = false) private Long runId;
    @Column(name = "plan_id", nullable = false) private Long planId;
    @Column(name = "step_key", nullable = false, length = 64) private String stepKey;
    @Column(name = "step_order", nullable = false) private int stepOrder;
    @Column(name = "step_kind", nullable = false, length = 32) private String stepKind;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "depends_on_json", nullable = false, columnDefinition = "TEXT") private String dependsOnJson;
    @Column(name = "allowed_tool_names_json", nullable = false, columnDefinition = "TEXT") private String allowedToolNamesJson;
    @Column(name = "expected_evidence_json", nullable = false, columnDefinition = "TEXT") private String expectedEvidenceJson;
    @Column(name = "attempt_no", nullable = false) private int attemptNo;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "result_summary", length = 1024) private String resultSummary;
    @Column(name = "lease_owner", length = 128) private String leaseOwner;
    @Column(name = "lease_expires_at") private Instant leaseExpiresAt;
    @Version @Column(nullable = false) private Long version;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected AgentTaskStepEntity() { }
    public AgentTaskStepEntity(String orgId, long runId, long planId, String stepKey, int stepOrder, String stepKind,
                               String dependsOnJson, String allowedToolNamesJson, String expectedEvidenceJson, boolean ready, Instant now) {
        this.orgId = orgId; this.runId = runId; this.planId = planId; this.stepKey = stepKey; this.stepOrder = stepOrder;
        this.stepKind = stepKind; this.status = ready ? STATUS_READY : STATUS_PENDING; this.dependsOnJson = dependsOnJson;
        this.allowedToolNamesJson = allowedToolNamesJson; this.expectedEvidenceJson = expectedEvidenceJson;
        this.createdAt = now; this.updatedAt = now;
    }
    public void markReady(Instant now) { if (STATUS_PENDING.equals(status)) { status = STATUS_READY; updatedAt = now; } }
    public void claim(String owner, Instant expiresAt, Instant now) { status = STATUS_RUNNING; leaseOwner = owner; leaseExpiresAt = expiresAt; attemptNo++; startedAt = now; updatedAt = now; }
    public void succeed(String summary, Instant now) { status = STATUS_SUCCEEDED; resultSummary = summary; completedAt = now; leaseOwner = null; leaseExpiresAt = null; updatedAt = now; }
    public void fail(String errorCode, Instant now) { status = STATUS_FAILED; this.errorCode = errorCode; completedAt = now; leaseOwner = null; leaseExpiresAt = null; updatedAt = now; }
    public void recover(Instant now) { status = STATUS_READY; leaseOwner = null; leaseExpiresAt = null; updatedAt = now; }
    public Long getId() { return id; } public String getOrgId() { return orgId; } public Long getRunId() { return runId; }
    public Long getPlanId() { return planId; } public String getStepKey() { return stepKey; } public int getStepOrder() { return stepOrder; }
    public String getStepKind() { return stepKind; } public String getStatus() { return status; } public String getDependsOnJson() { return dependsOnJson; }
    public String getAllowedToolNamesJson() { return allowedToolNamesJson; } public String getExpectedEvidenceJson() { return expectedEvidenceJson; }
    public int getAttemptNo() { return attemptNo; } public String getErrorCode() { return errorCode; } public String getResultSummary() { return resultSummary; }
    public String getLeaseOwner() { return leaseOwner; } public Instant getLeaseExpiresAt() { return leaseExpiresAt; } public Long getVersion() { return version; }
    public Instant getStartedAt() { return startedAt; } public Instant getCompletedAt() { return completedAt; }
}
