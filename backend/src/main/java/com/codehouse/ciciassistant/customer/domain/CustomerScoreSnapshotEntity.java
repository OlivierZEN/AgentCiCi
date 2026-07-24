package com.codehouse.ciciassistant.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "customer_score_snapshot", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "crm_account_id"}))
public class CustomerScoreSnapshotEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "company_id", nullable = false, length = 64) private String companyId;
    @Column(name = "crm_account_id", nullable = false, length = 128) private String crmAccountId;
    @Column(name = "health_score", nullable = false) private int healthScore;
    @Column(name = "health_dimension_score", nullable = false) private int healthDimensionScore;
    @Column(name = "expansion_score", nullable = false) private int expansionScore;
    @Column(name = "renewal_score", nullable = false) private int renewalScore;
    @Column(name = "relationship_score", nullable = false) private int relationshipScore;
    @Column(name = "risk_score", nullable = false) private int riskScore;
    @Column(name = "net_change_30d", nullable = false) private double netChange30d;
    @Column(name = "active_signal_count", nullable = false) private int activeSignalCount;
    @Column(name = "pending_signal_count", nullable = false) private int pendingSignalCount;
    @Column(name = "calculation_version", nullable = false, length = 64) private String calculationVersion;
    @Column(name = "calculated_at", nullable = false) private Instant calculatedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CustomerScoreSnapshotEntity() {}
    public CustomerScoreSnapshotEntity(String companyId, String crmAccountId) {
        this.companyId = companyId; this.crmAccountId = crmAccountId; this.createdAt = Instant.now();
        update(50, 50, 50, 50, 50, 50, 0, 0, 0, "ai-evidence-v1");
    }
    public void update(int healthScore, int healthDimensionScore, int expansionScore, int renewalScore,
                       int relationshipScore, int riskScore, double netChange30d,
                       int activeSignalCount, int pendingSignalCount, String calculationVersion) {
        this.healthScore = healthScore; this.healthDimensionScore = healthDimensionScore;
        this.expansionScore = expansionScore; this.renewalScore = renewalScore;
        this.relationshipScore = relationshipScore; this.riskScore = riskScore;
        this.netChange30d = netChange30d; this.activeSignalCount = activeSignalCount;
        this.pendingSignalCount = pendingSignalCount; this.calculationVersion = calculationVersion;
        this.calculatedAt = Instant.now(); this.updatedAt = this.calculatedAt;
    }
    public String getCompanyId() { return companyId; }
    public String getCrmAccountId() { return crmAccountId; }
    public int getHealthScore() { return healthScore; }
    public int getHealthDimensionScore() { return healthDimensionScore; }
    public int getExpansionScore() { return expansionScore; }
    public int getRenewalScore() { return renewalScore; }
    public int getRelationshipScore() { return relationshipScore; }
    public int getRiskScore() { return riskScore; }
    public double getNetChange30d() { return netChange30d; }
    public int getActiveSignalCount() { return activeSignalCount; }
    public int getPendingSignalCount() { return pendingSignalCount; }
    public String getCalculationVersion() { return calculationVersion; }
    public Instant getCalculatedAt() { return calculatedAt; }
}
