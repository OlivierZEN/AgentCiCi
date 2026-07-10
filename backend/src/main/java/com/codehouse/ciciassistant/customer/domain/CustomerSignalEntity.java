package com.codehouse.ciciassistant.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_signal")
public class CustomerSignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;
    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;
    @Column(name = "crm_account_id", nullable = false, length = 128)
    private String crmAccountId;
    @Column(name = "mode", nullable = false, length = 32)
    private String mode;
    @Column(name = "signal_type", nullable = false, length = 64)
    private String signalType;
    @Column(name = "title", nullable = false, length = 256)
    private String title;
    @Column(name = "detail", nullable = false, columnDefinition = "TEXT")
    private String detail;
    @Column(name = "severity", nullable = false, length = 32)
    private String severity;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "evidence_json", nullable = false, columnDefinition = "TEXT")
    private String evidenceJson;
    @Column(name = "assignee", length = 128)
    private String assignee;
    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerSignalEntity() {}

    public CustomerSignalEntity(String publicId, String orgId, String crmAccountId, String mode,
                                String signalType, String title, String detail, String severity,
                                String evidenceJson, String assignee, Instant sourceUpdatedAt) {
        this.publicId = publicId;
        this.orgId = orgId;
        this.crmAccountId = crmAccountId;
        this.mode = mode;
        this.signalType = signalType;
        this.title = title;
        this.detail = detail;
        this.severity = severity;
        this.status = "OPEN";
        this.evidenceJson = evidenceJson;
        this.assignee = assignee;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getPublicId() { return publicId; }
    public String getOrgId() { return orgId; }
    public String getCrmAccountId() { return crmAccountId; }
    public String getMode() { return mode; }
    public String getSignalType() { return signalType; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public String getEvidenceJson() { return evidenceJson; }
    public String getAssignee() { return assignee; }
    public Instant getSourceUpdatedAt() { return sourceUpdatedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void refresh(String mode, String title, String detail, String severity,
                        String evidenceJson, Instant sourceUpdatedAt) {
        this.mode = mode;
        this.title = title;
        this.detail = detail;
        this.severity = severity;
        this.status = "OPEN";
        this.evidenceJson = evidenceJson;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.updatedAt = Instant.now();
    }

    public void resolve() {
        if (!"RESOLVED".equals(this.status)) {
            this.status = "RESOLVED";
            this.updatedAt = Instant.now();
        }
    }
}
