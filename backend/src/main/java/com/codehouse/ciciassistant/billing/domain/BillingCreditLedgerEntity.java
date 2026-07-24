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
@Table(name = "billing_credit_ledger")
public class BillingCreditLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "entry_type", nullable = false, length = 64)
    private String entryType;

    @Column(name = "credits_delta", nullable = false, precision = 18, scale = 2)
    private BigDecimal creditsDelta;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "source_event_id")
    private Long sourceEventId;

    @Column(name = "description", nullable = false, length = 1000)
    private String description = "";

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "metadata_json", nullable = false, columnDefinition = "TEXT")
    private String metadataJson = "{}";

    protected BillingCreditLedgerEntity() {
    }

    public BillingCreditLedgerEntity(String companyId,
                                     String entryType,
                                     BigDecimal creditsDelta,
                                     BigDecimal balanceAfter,
                                     Long sourceEventId,
                                     String description,
                                     Instant occurredAt,
                                     String metadataJson) {
        this.companyId = companyId;
        this.entryType = entryType;
        this.creditsDelta = creditsDelta;
        this.balanceAfter = balanceAfter;
        this.sourceEventId = sourceEventId;
        this.description = description;
        this.occurredAt = occurredAt;
        this.metadataJson = metadataJson;
    }

    public Long getId() { return id; }

    public String getCompanyId() { return companyId; }

    public String getEntryType() { return entryType; }

    public BigDecimal getCreditsDelta() { return creditsDelta; }

    public BigDecimal getBalanceAfter() { return balanceAfter; }

    public Long getSourceEventId() { return sourceEventId; }

    public String getDescription() { return description; }

    public Instant getOccurredAt() { return occurredAt; }

    public String getMetadataJson() { return metadataJson; }
}
