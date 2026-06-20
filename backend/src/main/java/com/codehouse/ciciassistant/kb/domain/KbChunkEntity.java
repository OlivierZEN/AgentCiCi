package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_chunk")
public class KbChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "knowledge_base_id", nullable = false, length = 64)
    private String knowledgeBaseId;

    @Column(name = "content", nullable = false, length = 4000)
    private String content;

    @Column(name = "tags", length = 256)
    private String tags;

    @Column(name = "vector_id", length = 128)
    private String vectorId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "embedding_provider", length = 64)
    private String embeddingProvider;

    @Column(name = "embedding_model", length = 128)
    private String embeddingModel;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected KbChunkEntity() {
    }

    public KbChunkEntity(String orgId, String knowledgeBaseId, String content, String tags) {
        this(orgId, knowledgeBaseId, null, null, content, tags, null, null);
    }

    public KbChunkEntity(String orgId, String knowledgeBaseId, String content, String tags, String vectorId) {
        this(orgId, knowledgeBaseId, null, null, content, tags, vectorId, null);
    }

    public KbChunkEntity(String orgId,
                         String knowledgeBaseId,
                         Long documentId,
                         Integer chunkIndex,
                         String content,
                         String tags,
                         String vectorId,
                         String contentHash) {
        this.orgId = orgId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.tags = tags;
        this.vectorId = vectorId;
        this.contentHash = contentHash;
        this.status = "ACTIVE";
        this.enabled = true;
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public String getContent() {
        return content;
    }

    public String getTags() {
        return tags;
    }

    public String getVectorId() {
        return vectorId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getEmbeddingProvider() {
        return embeddingProvider;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public Integer getEmbeddingDimension() {
        return embeddingDimension;
    }

    public String getStatus() {
        return status;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }

    public void setEmbeddingMetadata(String provider, String model, Integer dimension) {
        this.embeddingProvider = provider;
        this.embeddingModel = model;
        this.embeddingDimension = dimension;
    }

    public void markDeleted() {
        this.status = "DELETED";
        this.enabled = false;
        this.deletedAt = Instant.now();
    }

    public void disable() {
        this.status = "DISABLED";
        this.enabled = false;
    }

    public void enable() {
        this.status = "ACTIVE";
        this.enabled = true;
        this.deletedAt = null;
    }

    public void updateContent(String content, String contentHash) {
        this.content = content;
        this.contentHash = contentHash;
        this.status = "ACTIVE";
        this.enabled = true;
    }

    public boolean isSearchable() {
        return enabled && deletedAt == null && "ACTIVE".equals(status);
    }
}
