package com.codehouse.ciciassistant.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "organization_purge_job")
public class OrganizationPurgeJobEntity {

    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PARTIAL_FAILED = "PARTIAL_FAILED";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";
    public static final String PHASE_DRY_RUN_MANIFEST = "DRY_RUN_MANIFEST";
    public static final String PHASE_REAL_PURGE = "REAL_PURGE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "phase", nullable = false, length = 64)
    private String phase;

    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "manifest_json", nullable = false, columnDefinition = "TEXT")
    private String manifestJson;

    @Column(name = "source_dry_run_job_id")
    private Long sourceDryRunJobId;

    @Column(name = "confirmation_text", length = 128)
    private String confirmationText;

    @Column(name = "manifest_version", nullable = false, length = 32)
    private String manifestVersion;

    @Column(name = "manifest_hash", length = 128)
    private String manifestHash;

    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "worker_id", length = 128)
    private String workerId;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "lock_expires_at")
    private Instant lockExpiresAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "dead_letter_at")
    private Instant deadLetterAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizationPurgeJobEntity() {
    }

    public OrganizationPurgeJobEntity(String orgId, String requestedBy, String reason, String manifestJson) {
        Instant now = Instant.now();
        this.orgId = orgId;
        this.dryRun = true;
        this.status = STATUS_SUCCEEDED;
        this.phase = PHASE_DRY_RUN_MANIFEST;
        this.requestedBy = requestedBy;
        this.reason = reason == null || reason.isBlank() ? null : reason.trim();
        this.startedAt = now;
        this.finishedAt = now;
        this.manifestJson = manifestJson == null || manifestJson.isBlank() ? "{}" : manifestJson;
        this.manifestVersion = "v2";
        this.resultJson = "{}";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static OrganizationPurgeJobEntity realPurge(String orgId,
                                                       String requestedBy,
                                                       String reason,
                                                       Long sourceDryRunJobId,
                                                       String confirmationText,
                                                       String manifestJson,
                                                       String manifestHash) {
        Instant now = Instant.now();
        OrganizationPurgeJobEntity job = new OrganizationPurgeJobEntity();
        job.orgId = orgId;
        job.dryRun = false;
        job.status = STATUS_QUEUED;
        job.phase = PHASE_REAL_PURGE;
        job.requestedBy = requestedBy == null || requestedBy.isBlank() ? "platform" : requestedBy.trim();
        job.reason = reason == null || reason.isBlank() ? null : reason.trim();
        job.sourceDryRunJobId = sourceDryRunJobId;
        job.confirmationText = confirmationText;
        job.scheduledAt = now;
        job.manifestJson = manifestJson == null || manifestJson.isBlank() ? "{}" : manifestJson;
        job.manifestVersion = "v2";
        job.manifestHash = manifestHash;
        job.resultJson = "{}";
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    public void markRunning() {
        Instant now = Instant.now();
        this.status = STATUS_RUNNING;
        this.startedAt = now;
        this.finishedAt = null;
        this.errorMessage = null;
        this.updatedAt = now;
    }

    public void markRunning(String workerId, Instant lockedAt, Instant lockExpiresAt) {
        this.status = STATUS_RUNNING;
        this.workerId = workerId;
        this.lockedAt = lockedAt;
        this.lockExpiresAt = lockExpiresAt;
        this.startedAt = lockedAt;
        this.finishedAt = null;
        this.errorMessage = null;
        this.updatedAt = lockedAt;
        this.attemptCount++;
    }

    public void markFinished(String status, String resultJson, String errorMessage) {
        this.status = status;
        this.resultJson = resultJson == null || resultJson.isBlank() ? "{}" : resultJson;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
        this.lockExpiresAt = null;
        this.updatedAt = this.finishedAt;
    }

    public void markCanceled(String reason) {
        Instant now = Instant.now();
        this.status = STATUS_CANCELED;
        this.errorMessage = reason == null || reason.isBlank() ? "Canceled before execution" : reason.trim();
        this.finishedAt = now;
        this.updatedAt = now;
    }

    public void markDeadLetter(String resultJson, String reason) {
        Instant now = Instant.now();
        this.status = STATUS_DEAD_LETTER;
        this.resultJson = resultJson == null || resultJson.isBlank() ? "{}" : resultJson;
        this.errorMessage = reason == null || reason.isBlank() ? "Purge worker lease expired" : reason.trim();
        this.deadLetterAt = now;
        this.finishedAt = now;
        this.lockExpiresAt = null;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public String getStatus() {
        return status;
    }

    public String getPhase() {
        return phase;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getManifestJson() {
        return manifestJson;
    }

    public Long getSourceDryRunJobId() {
        return sourceDryRunJobId;
    }

    public String getConfirmationText() {
        return confirmationText;
    }

    public String getManifestVersion() {
        return manifestVersion;
    }

    public String getManifestHash() {
        return manifestHash;
    }

    public String getResultJson() {
        return resultJson;
    }

    public String getWorkerId() {
        return workerId;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public Instant getLockExpiresAt() {
        return lockExpiresAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getDeadLetterAt() {
        return deadLetterAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
