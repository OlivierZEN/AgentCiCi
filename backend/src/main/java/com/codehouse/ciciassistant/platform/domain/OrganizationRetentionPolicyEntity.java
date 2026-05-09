package com.codehouse.ciciassistant.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "organization_retention_policy")
public class OrganizationRetentionPolicyEntity {

    @Id
    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "grace_until")
    private Instant graceUntil;

    @Column(name = "suspend_until")
    private Instant suspendUntil;

    @Column(name = "export_deadline")
    private Instant exportDeadline;

    @Column(name = "purge_after")
    private Instant purgeAfter;

    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold;

    @Column(name = "legal_hold_reason", length = 512)
    private String legalHoldReason;

    @Column(name = "legal_hold_approved_by", length = 64)
    private String legalHoldApprovedBy;

    @Column(name = "legal_hold_approved_at")
    private Instant legalHoldApprovedAt;

    @Column(name = "legal_hold_review_at")
    private Instant legalHoldReviewAt;

    @Column(name = "policy_source", nullable = false, length = 64)
    private String policySource;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizationRetentionPolicyEntity() {
    }

    public OrganizationRetentionPolicyEntity(String orgId) {
        Instant now = Instant.now();
        this.orgId = orgId;
        this.legalHold = false;
        this.policySource = "PLATFORM_MANUAL";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(Instant graceUntil,
                       Instant suspendUntil,
                       Instant exportDeadline,
                       Instant purgeAfter,
                       boolean legalHold,
                       String policySource,
                       String legalHoldReason,
                       String legalHoldApprovedBy,
                       Instant legalHoldApprovedAt,
                       Instant legalHoldReviewAt) {
        this.graceUntil = graceUntil;
        this.suspendUntil = suspendUntil;
        this.exportDeadline = exportDeadline;
        this.purgeAfter = purgeAfter;
        this.legalHold = legalHold;
        this.policySource = policySource == null || policySource.isBlank() ? "PLATFORM_MANUAL" : policySource.trim();
        this.legalHoldReason = legalHold ? trimToNull(legalHoldReason) : null;
        this.legalHoldApprovedBy = legalHold ? trimToNull(legalHoldApprovedBy) : null;
        this.legalHoldApprovedAt = legalHold ? legalHoldApprovedAt : null;
        this.legalHoldReviewAt = legalHold ? legalHoldReviewAt : null;
        this.updatedAt = Instant.now();
    }

    public String getOrgId() {
        return orgId;
    }

    public Instant getGraceUntil() {
        return graceUntil;
    }

    public Instant getSuspendUntil() {
        return suspendUntil;
    }

    public Instant getExportDeadline() {
        return exportDeadline;
    }

    public Instant getPurgeAfter() {
        return purgeAfter;
    }

    public boolean isLegalHold() {
        return legalHold;
    }

    public String getPolicySource() {
        return policySource;
    }

    public String getLegalHoldReason() {
        return legalHoldReason;
    }

    public String getLegalHoldApprovedBy() {
        return legalHoldApprovedBy;
    }

    public Instant getLegalHoldApprovedAt() {
        return legalHoldApprovedAt;
    }

    public Instant getLegalHoldReviewAt() {
        return legalHoldReviewAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
