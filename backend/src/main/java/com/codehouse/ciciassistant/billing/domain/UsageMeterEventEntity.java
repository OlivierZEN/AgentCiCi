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
@Table(name = "usage_meter_event")
public class UsageMeterEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "agent_id", length = 64)
    private String agentId;

    @Column(name = "billable_domain", nullable = false, length = 64)
    private String billableDomain;

    @Column(name = "billable_item_code", nullable = false, length = 128)
    private String billableItemCode;

    @Column(name = "description", nullable = false, length = 1000)
    private String description = "";

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit", nullable = false, length = 32)
    private String unit = "run";

    @Column(name = "work_credit_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal workCreditQuantity = BigDecimal.ZERO;

    @Column(name = "billing_type", nullable = false, length = 32)
    private String billingType = "included";

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType = "manual";

    @Column(name = "source_id", nullable = false, length = 128)
    private String sourceId = "";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "succeeded";

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "metadata_json", nullable = false, columnDefinition = "TEXT")
    private String metadataJson = "{}";

    protected UsageMeterEventEntity() {
    }

    public UsageMeterEventEntity(String orgId,
                                 String userId,
                                 String agentId,
                                 String billableDomain,
                                 String billableItemCode,
                                 String description,
                                 BigDecimal quantity,
                                 String unit,
                                 BigDecimal workCreditQuantity,
                                 String billingType,
                                 String sourceType,
                                 String sourceId,
                                 Instant occurredAt,
                                 String metadataJson) {
        this.orgId = orgId;
        this.userId = userId;
        this.agentId = agentId;
        this.billableDomain = billableDomain;
        this.billableItemCode = billableItemCode;
        this.description = description;
        this.quantity = quantity;
        this.unit = unit;
        this.workCreditQuantity = workCreditQuantity;
        this.billingType = billingType;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.occurredAt = occurredAt;
        this.metadataJson = metadataJson;
    }

    public Long getId() { return id; }

    public String getOrgId() { return orgId; }

    public String getUserId() { return userId; }

    public String getAgentId() { return agentId; }

    public String getBillableDomain() { return billableDomain; }

    public String getBillableItemCode() { return billableItemCode; }

    public String getDescription() { return description; }

    public BigDecimal getQuantity() { return quantity; }

    public String getUnit() { return unit; }

    public BigDecimal getWorkCreditQuantity() { return workCreditQuantity; }

    public String getBillingType() { return billingType; }

    public String getSourceType() { return sourceType; }

    public String getSourceId() { return sourceId; }

    public String getStatus() { return status; }

    public Instant getOccurredAt() { return occurredAt; }

    public String getMetadataJson() { return metadataJson; }
}
