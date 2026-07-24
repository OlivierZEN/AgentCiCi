package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_sync_job")
public class KbSyncJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;

    @Column(name = "trigger_type", nullable = false, length = 32)
    private String triggerType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "sync_cursor_before", columnDefinition = "TEXT")
    private String syncCursorBefore;

    @Column(name = "sync_cursor_after", columnDefinition = "TEXT")
    private String syncCursorAfter;

    @Column(name = "document_count", nullable = false)
    private int documentCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KbSyncJobEntity() {
    }

    public KbSyncJobEntity(String companyId, Long knowledgeBaseId, Long dataSourceId, String triggerType, String syncCursorBefore) {
        Instant now = Instant.now();
        this.companyId = companyId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.dataSourceId = dataSourceId;
        this.triggerType = triggerType == null || triggerType.isBlank() ? "MANUAL" : triggerType;
        this.status = "RUNNING";
        this.syncCursorBefore = syncCursorBefore;
        this.startedAt = now;
        this.createdAt = now;
    }

    public void markSuccess(String syncCursorAfter, int documentCount, int chunkCount) {
        this.status = "SUCCEEDED";
        this.syncCursorAfter = syncCursorAfter;
        this.documentCount = documentCount;
        this.chunkCount = chunkCount;
        this.errorMessage = null;
        this.finishedAt = Instant.now();
    }

    public void markFailed(String message) {
        this.status = "FAILED";
        this.errorMessage = message == null ? "" : message.length() <= 1000 ? message : message.substring(0, 1000);
        this.finishedAt = Instant.now();
    }

    public Long getId() { return id; }

    public String getCompanyId() { return companyId; }

    public Long getKnowledgeBaseId() { return knowledgeBaseId; }

    public Long getDataSourceId() { return dataSourceId; }

    public String getTriggerType() { return triggerType; }

    public String getStatus() { return status; }

    public String getSyncCursorBefore() { return syncCursorBefore; }

    public String getSyncCursorAfter() { return syncCursorAfter; }

    public int getDocumentCount() { return documentCount; }

    public int getChunkCount() { return chunkCount; }

    public String getErrorMessage() { return errorMessage; }

    public Instant getStartedAt() { return startedAt; }

    public Instant getFinishedAt() { return finishedAt; }

    public Instant getCreatedAt() { return createdAt; }
}
