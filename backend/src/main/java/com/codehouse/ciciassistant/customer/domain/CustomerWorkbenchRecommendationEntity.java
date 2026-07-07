package com.codehouse.ciciassistant.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "customer_workbench_recommendation")
public class CustomerWorkbenchRecommendationEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_DISMISSED = "DISMISSED";
    public static final String STATUS_APPLIED = "APPLIED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "crm_account_id", nullable = false, length = 128)
    private String crmAccountId;

    @Column(name = "recommendation_type", nullable = false, length = 48)
    private String recommendationType;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "rationale", nullable = false, columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "confidence", nullable = false)
    private BigDecimal confidence;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "crm_payload", nullable = false, columnDefinition = "TEXT")
    private String crmPayload;

    @Column(name = "applied_crm_id", length = 128)
    private String appliedCrmId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerWorkbenchRecommendationEntity() {
    }

    public CustomerWorkbenchRecommendationEntity(String publicId,
                                                 String orgId,
                                                 String crmAccountId,
                                                 String recommendationType,
                                                 String title,
                                                 String rationale,
                                                 BigDecimal confidence,
                                                 String crmPayload) {
        this.publicId = publicId;
        this.orgId = orgId;
        this.crmAccountId = crmAccountId;
        this.recommendationType = recommendationType;
        this.title = title;
        this.rationale = rationale;
        this.confidence = confidence;
        this.status = STATUS_PENDING;
        this.crmPayload = crmPayload;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getOrgId() { return orgId; }
    public String getCrmAccountId() { return crmAccountId; }
    public String getRecommendationType() { return recommendationType; }
    public String getTitle() { return title; }
    public String getRationale() { return rationale; }
    public BigDecimal getConfidence() { return confidence; }
    public String getStatus() { return status; }
    public String getCrmPayload() { return crmPayload; }
    public String getAppliedCrmId() { return appliedCrmId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void accept() {
        if (!STATUS_APPLIED.equals(this.status)) {
            this.status = STATUS_ACCEPTED;
            this.updatedAt = Instant.now();
        }
    }

    public void apply(String appliedCrmId) {
        this.status = STATUS_APPLIED;
        this.appliedCrmId = appliedCrmId;
        this.updatedAt = Instant.now();
    }
}
