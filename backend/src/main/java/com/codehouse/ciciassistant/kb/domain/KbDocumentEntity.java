package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_document")
public class KbDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "index_version", nullable = false)
    private Integer indexVersion;

    protected KbDocumentEntity() {
    }

    public KbDocumentEntity(String companyId, Long knowledgeBaseId, String name, String contentType, String storagePath) {
        this(companyId, knowledgeBaseId, name, contentType, storagePath, null);
    }

    public KbDocumentEntity(String companyId, Long knowledgeBaseId, String name, String contentType, String storagePath, Long fileSize) {
        this.companyId = companyId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.name = name;
        this.contentType = contentType;
        this.storagePath = storagePath;
        this.status = "UPLOADED";
        this.createdAt = Instant.now();
        this.fileSize = fileSize;
        this.enabled = true;
        this.archived = false;
        this.updatedAt = this.createdAt;
        this.indexVersion = 1;
    }

    public Long getId() {
        return id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public String getName() {
        return name;
    }

    public String getContentType() {
        return contentType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isArchived() {
        return archived;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt == null ? createdAt : updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Integer getIndexVersion() {
        return indexVersion == null ? 1 : indexVersion;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void markIndexing() {
        this.status = "INDEXING";
        this.errorMessage = null;
        this.indexVersion = getIndexVersion() + 1;
        this.updatedAt = Instant.now();
    }

    public void markPublished() {
        this.status = "PUBLISHED";
        this.enabled = true;
        this.archived = false;
        this.errorMessage = null;
        this.indexedAt = Instant.now();
        this.updatedAt = this.indexedAt;
    }

    public void markFailed(String message) {
        this.status = "FAILED";
        this.errorMessage = truncate(message);
        this.updatedAt = Instant.now();
    }

    public void markUnpublished() {
        this.status = "UNPUBLISHED";
        this.updatedAt = Instant.now();
    }

    public void markCleanupFailed(String message) {
        this.status = "CLEANUP_FAILED";
        this.errorMessage = truncate(message);
        this.updatedAt = Instant.now();
    }

    public void markDeleting() {
        this.status = "DELETING";
        this.updatedAt = Instant.now();
    }

    public void markDeleted() {
        this.status = "DELETED";
        this.enabled = false;
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Document name is required");
        }
        this.name = name.trim();
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
        this.updatedAt = Instant.now();
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
