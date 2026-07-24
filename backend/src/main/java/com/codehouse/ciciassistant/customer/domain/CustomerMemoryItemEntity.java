package com.codehouse.ciciassistant.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_memory_item")
public class CustomerMemoryItemEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;
    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;
    @Column(name = "crm_account_id", nullable = false, length = 128)
    private String crmAccountId;
    @Column(name = "source_event_id", nullable = false, length = 64)
    private String sourceEventId;
    @Column(name = "source_batch_id", length = 64)
    private String sourceBatchId;
    @Column(name = "memory_type", nullable = false, length = 32)
    private String memoryType;
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "confidence", nullable = false)
    private double confidence;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "valid_until")
    private Instant validUntil;
    @Column(name = "evidence_json", nullable = false, columnDefinition = "TEXT")
    private String evidenceJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerMemoryItemEntity() {}

    public CustomerMemoryItemEntity(String publicId, String companyId, String crmAccountId,
                                    String sourceEventId, String sourceBatchId, String memoryType,
                                    String content, double confidence, Instant occurredAt,
                                    String evidenceJson) {
        this.publicId = publicId;
        this.companyId = companyId;
        this.crmAccountId = crmAccountId;
        this.sourceEventId = sourceEventId;
        this.sourceBatchId = sourceBatchId;
        this.memoryType = memoryType;
        this.content = content;
        this.status = STATUS_ACTIVE;
        this.confidence = confidence;
        this.occurredAt = occurredAt;
        this.evidenceJson = evidenceJson;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getCompanyId() { return companyId; }
    public String getCrmAccountId() { return crmAccountId; }
    public String getSourceEventId() { return sourceEventId; }
    public String getSourceBatchId() { return sourceBatchId; }
    public String getMemoryType() { return memoryType; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
    public double getConfidence() { return confidence; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getValidUntil() { return validUntil; }
    public String getEvidenceJson() { return evidenceJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
