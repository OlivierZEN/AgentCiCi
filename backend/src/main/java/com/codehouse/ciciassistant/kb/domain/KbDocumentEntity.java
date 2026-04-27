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

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

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

    protected KbDocumentEntity() {
    }

    public KbDocumentEntity(String orgId, Long knowledgeBaseId, String name, String contentType, String storagePath) {
        this.orgId = orgId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.name = name;
        this.contentType = contentType;
        this.storagePath = storagePath;
        this.status = "UPLOADED";
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
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

    public void setStatus(String status) {
        this.status = status;
    }
}
