package com.codehouse.ciciassistant.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_interaction_asset")
public class CustomerInteractionAssetEntity {

    public static final String STATUS_STORED = "STORED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 64)
    private String publicId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "input_type", nullable = false, length = 32)
    private String inputType;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_path", nullable = false, length = 768)
    private String storagePath;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "extracted_text", nullable = false, columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "error_message", nullable = false, length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerInteractionAssetEntity() {
    }

    public CustomerInteractionAssetEntity(String publicId, Long batchId, String orgId, String inputType,
                                          String originalName, String contentType, long fileSize,
                                          String sha256, String storagePath, int sortOrder) {
        this.publicId = publicId;
        this.batchId = batchId;
        this.orgId = orgId;
        this.inputType = inputType;
        this.originalName = originalName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.storagePath = storagePath;
        this.sortOrder = sortOrder;
        this.status = STATUS_STORED;
        this.extractedText = "";
        this.errorMessage = "";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markProcessing() { status = STATUS_PROCESSING; errorMessage = ""; updatedAt = Instant.now(); }
    public void markReady(String extractedText) { status = STATUS_READY; this.extractedText = extractedText; errorMessage = ""; updatedAt = Instant.now(); }
    public void markFailed(String errorMessage) { status = STATUS_FAILED; this.errorMessage = errorMessage == null ? "提取失败" : errorMessage; updatedAt = Instant.now(); }
    public void resetForRetry() { status = STATUS_STORED; errorMessage = ""; updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Long getBatchId() { return batchId; }
    public String getOrgId() { return orgId; }
    public String getInputType() { return inputType; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public String getSha256() { return sha256; }
    public String getStoragePath() { return storagePath; }
    public int getSortOrder() { return sortOrder; }
    public String getStatus() { return status; }
    public String getExtractedText() { return extractedText; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

