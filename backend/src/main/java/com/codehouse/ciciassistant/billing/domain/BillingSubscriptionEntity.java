package com.codehouse.ciciassistant.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "billing_subscription")
public class BillingSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, unique = true, length = 64)
    private String companyId;

    @Column(name = "deployment_mode", nullable = false, length = 32)
    private String deploymentMode;

    @Column(name = "edition_code", nullable = false, length = 64)
    private String editionCode;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "active";

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "included_credits", nullable = false, precision = 18, scale = 2)
    private BigDecimal includedCredits = BigDecimal.ZERO;

    @Column(name = "consumed_credits", nullable = false, precision = 18, scale = 2)
    private BigDecimal consumedCredits = BigDecimal.ZERO;

    @Column(name = "remaining_credits", nullable = false, precision = 18, scale = 2)
    private BigDecimal remainingCredits = BigDecimal.ZERO;

    @Column(name = "operation_seats_used", nullable = false)
    private int operationSeatsUsed;

    @Column(name = "builder_seats_used", nullable = false)
    private int builderSeatsUsed;

    @Column(name = "package_codes", nullable = false, columnDefinition = "TEXT")
    private String packageCodes = "[]";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected BillingSubscriptionEntity() {
    }

    public BillingSubscriptionEntity(String companyId, String deploymentMode, String editionCode, Instant periodStart, Instant periodEnd) {
        this.companyId = companyId;
        this.deploymentMode = deploymentMode;
        this.editionCode = editionCode;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public Long getId() { return id; }

    public String getCompanyId() { return companyId; }

    public String getDeploymentMode() { return deploymentMode; }

    public void setDeploymentMode(String deploymentMode) { this.deploymentMode = deploymentMode; }

    public String getEditionCode() { return editionCode; }

    public void setEditionCode(String editionCode) { this.editionCode = editionCode; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public Instant getPeriodStart() { return periodStart; }

    public void setPeriodStart(Instant periodStart) { this.periodStart = periodStart; }

    public Instant getPeriodEnd() { return periodEnd; }

    public void setPeriodEnd(Instant periodEnd) { this.periodEnd = periodEnd; }

    public BigDecimal getIncludedCredits() { return includedCredits; }

    public void setIncludedCredits(BigDecimal includedCredits) { this.includedCredits = includedCredits; }

    public BigDecimal getConsumedCredits() { return consumedCredits; }

    public void setConsumedCredits(BigDecimal consumedCredits) { this.consumedCredits = consumedCredits; }

    public BigDecimal getRemainingCredits() { return remainingCredits; }

    public void setRemainingCredits(BigDecimal remainingCredits) { this.remainingCredits = remainingCredits; }

    public int getOperationSeatsUsed() { return operationSeatsUsed; }

    public void setOperationSeatsUsed(int operationSeatsUsed) { this.operationSeatsUsed = operationSeatsUsed; }

    public int getBuilderSeatsUsed() { return builderSeatsUsed; }

    public void setBuilderSeatsUsed(int builderSeatsUsed) { this.builderSeatsUsed = builderSeatsUsed; }

    public String getPackageCodes() { return packageCodes; }

    public void setPackageCodes(String packageCodes) { this.packageCodes = packageCodes; }

    public Instant getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
