package com.codehouse.ciciassistant.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_workbench_snapshot")
public class CustomerWorkbenchSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "crm_account_id", nullable = false, length = 128)
    private String crmAccountId;

    @Column(name = "account_name", nullable = false, length = 256)
    private String accountName;

    @Column(name = "owner_name", nullable = false, length = 128)
    private String ownerName;

    @Column(name = "segment", nullable = false, length = 32)
    private String segment;

    @Column(name = "health_score", nullable = false)
    private int healthScore;

    @Column(name = "progress_score", nullable = false)
    private int progressScore;

    @Column(name = "risk_count", nullable = false)
    private int riskCount;

    @Column(name = "next_action_count", nullable = false)
    private int nextActionCount;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerWorkbenchSnapshotEntity() {
    }

    public CustomerWorkbenchSnapshotEntity(String publicId,
                                           String companyId,
                                           String crmAccountId,
                                           String accountName,
                                           String ownerName,
                                           String segment,
                                           int healthScore,
                                           int progressScore,
                                           int riskCount,
                                           int nextActionCount,
                                           String snapshotJson) {
        this.publicId = publicId;
        this.companyId = companyId;
        this.crmAccountId = crmAccountId;
        this.accountName = accountName;
        this.ownerName = ownerName;
        this.segment = segment;
        this.healthScore = healthScore;
        this.progressScore = progressScore;
        this.riskCount = riskCount;
        this.nextActionCount = nextActionCount;
        this.snapshotJson = snapshotJson;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getCompanyId() { return companyId; }
    public String getCrmAccountId() { return crmAccountId; }
    public String getAccountName() { return accountName; }
    public String getOwnerName() { return ownerName; }
    public String getSegment() { return segment; }
    public int getHealthScore() { return healthScore; }
    public int getProgressScore() { return progressScore; }
    public int getRiskCount() { return riskCount; }
    public int getNextActionCount() { return nextActionCount; }
    public String getSnapshotJson() { return snapshotJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
