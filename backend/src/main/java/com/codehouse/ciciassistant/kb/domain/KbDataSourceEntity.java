package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_data_source")
public class KbDataSourceEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "sync_cursor", columnDefinition = "TEXT")
    private String syncCursor;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KbDataSourceEntity() {
    }

    public KbDataSourceEntity(String companyId, Long knowledgeBaseId, String sourceType, String name, String configJson) {
        Instant now = Instant.now();
        this.companyId = companyId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.sourceType = sourceType;
        this.name = name;
        this.configJson = configJson;
        this.status = STATUS_ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markSynced(String syncCursor) {
        this.status = STATUS_ACTIVE;
        this.errorMessage = null;
        this.syncCursor = syncCursor;
        this.lastSyncedAt = Instant.now();
        this.updatedAt = this.lastSyncedAt;
    }

    public void markError(String message) {
        this.status = STATUS_ERROR;
        this.errorMessage = truncate(message);
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }

    public String getCompanyId() { return companyId; }

    public Long getKnowledgeBaseId() { return knowledgeBaseId; }

    public String getSourceType() { return sourceType; }

    public String getName() { return name; }

    public String getConfigJson() { return configJson; }

    public String getSyncCursor() { return syncCursor; }

    public String getStatus() { return status; }

    public Instant getLastSyncedAt() { return lastSyncedAt; }

    public String getErrorMessage() { return errorMessage; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
