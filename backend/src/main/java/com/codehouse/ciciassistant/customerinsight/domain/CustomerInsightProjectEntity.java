package com.codehouse.ciciassistant.customerinsight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_insight_project")
public class CustomerInsightProjectEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ANALYZING = "ANALYZING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_ERROR = "ERROR";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "owner_user_id", nullable = false, length = 64)
    private String ownerUserId;

    @Column(name = "customer_name", nullable = false, length = 256)
    private String customerName;

    @Column(name = "customer_external_id", length = 128)
    private String customerExternalId;

    @Column(name = "customer_object_api_name", length = 128)
    private String customerObjectApiName;

    @Column(name = "industry", length = 128)
    private String industry;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "completeness_score", nullable = false)
    private int completenessScore;

    @Column(name = "latest_summary", columnDefinition = "TEXT")
    private String latestSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerInsightProjectEntity() {
    }

    public CustomerInsightProjectEntity(String publicId,
                                        String orgId,
                                        String ownerUserId,
                                        String customerName,
                                        String customerExternalId,
                                        String customerObjectApiName,
                                        String industry,
                                        String sourceType) {
        this.publicId = publicId;
        this.orgId = orgId;
        this.ownerUserId = ownerUserId;
        this.customerName = customerName;
        this.customerExternalId = customerExternalId;
        this.customerObjectApiName = customerObjectApiName;
        this.industry = industry;
        this.sourceType = sourceType == null || sourceType.isBlank() ? "MANUAL" : sourceType;
        this.status = STATUS_DRAFT;
        this.completenessScore = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getOrgId() { return orgId; }
    public String getOwnerUserId() { return ownerUserId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerExternalId() { return customerExternalId; }
    public String getCustomerObjectApiName() { return customerObjectApiName; }
    public String getIndustry() { return industry; }
    public String getSourceType() { return sourceType; }
    public String getStatus() { return status; }
    public int getCompletenessScore() { return completenessScore; }
    public String getLatestSummary() { return latestSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String customerName,
                       String customerExternalId,
                       String customerObjectApiName,
                       String industry,
                       String sourceType) {
        if (customerName != null && !customerName.isBlank()) {
            this.customerName = customerName.trim();
        }
        this.customerExternalId = blankToNull(customerExternalId);
        this.customerObjectApiName = blankToNull(customerObjectApiName);
        this.industry = blankToNull(industry);
        if (sourceType != null && !sourceType.isBlank()) {
            this.sourceType = sourceType.trim();
        }
        touch();
    }

    public void markAnalyzing() {
        this.status = STATUS_ANALYZING;
        touch();
    }

    public void markReady(String latestSummary, int completenessScore) {
        this.status = STATUS_READY;
        this.latestSummary = blankToNull(latestSummary);
        this.completenessScore = Math.max(0, Math.min(100, completenessScore));
        touch();
    }

    public void markError(String latestSummary) {
        this.status = STATUS_ERROR;
        this.latestSummary = blankToNull(latestSummary);
        touch();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
