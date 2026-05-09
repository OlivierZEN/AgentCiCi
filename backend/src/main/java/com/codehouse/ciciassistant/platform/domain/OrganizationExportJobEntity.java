package com.codehouse.ciciassistant.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "organization_export_job")
public class OrganizationExportJobEntity {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "file_path", length = 1024)
    private String filePath;

    @Column(name = "manifest_json", nullable = false, columnDefinition = "TEXT")
    private String manifestJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizationExportJobEntity() {
    }

    public OrganizationExportJobEntity(String orgId, String requestedBy, String reason) {
        Instant now = Instant.now();
        this.orgId = orgId;
        this.status = STATUS_RUNNING;
        this.requestedBy = requestedBy == null || requestedBy.isBlank() ? "system" : requestedBy.trim();
        this.reason = reason == null || reason.isBlank() ? null : reason.trim();
        this.manifestJson = "{}";
        this.startedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markSucceeded(String filePath, String manifestJson) {
        Instant now = Instant.now();
        this.status = STATUS_SUCCEEDED;
        this.filePath = filePath;
        this.manifestJson = manifestJson == null || manifestJson.isBlank() ? "{}" : manifestJson;
        this.finishedAt = now;
        this.updatedAt = now;
    }

    public void markFailed(String message, String manifestJson) {
        Instant now = Instant.now();
        this.status = STATUS_FAILED;
        this.errorMessage = message;
        this.manifestJson = manifestJson == null || manifestJson.isBlank() ? "{}" : manifestJson;
        this.finishedAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getStatus() {
        return status;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getReason() {
        return reason;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getManifestJson() {
        return manifestJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
