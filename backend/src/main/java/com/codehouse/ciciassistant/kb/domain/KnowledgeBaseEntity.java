package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "knowledge_base")
public class KnowledgeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "chunk_size", nullable = false)
    private Integer chunkSize;

    @Column(name = "chunk_overlap", nullable = false)
    private Integer chunkOverlap;

    @Column(name = "chunk_delimiter", length = 32)
    private String chunkDelimiter;

    @Column(name = "retrieval_strategy", length = 32)
    private String retrievalStrategy;

    @Column(name = "top_k", nullable = false)
    private Integer topK;

    @Column(name = "score_threshold", nullable = false)
    private Double scoreThreshold;

    @Column(name = "embedding_provider", nullable = false, length = 64)
    private String embeddingProvider;

    @Column(name = "embedding_model", nullable = false, length = 128)
    private String embeddingModel;

    @Column(name = "embedding_dimension", nullable = false)
    private Integer embeddingDimension;

    protected KnowledgeBaseEntity() {
    }

    public KnowledgeBaseEntity(String companyId, String name, String description) {
        this.companyId = companyId;
        this.name = name;
        this.description = description;
        this.status = "DRAFT";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.chunkSize = 280;
        this.chunkOverlap = 40;
        this.chunkDelimiter = "\n";
        this.retrievalStrategy = "VECTOR";
        this.topK = 5;
        this.scoreThreshold = 0.0;
        this.embeddingProvider = "local";
        this.embeddingModel = "local-hash";
        this.embeddingDimension = 1024;
    }

    public Long getId() {
        return id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt == null ? createdAt : updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Integer getChunkSize() {
        return chunkSize == null ? 280 : chunkSize;
    }

    public Integer getChunkOverlap() {
        return chunkOverlap == null ? 40 : chunkOverlap;
    }

    public String getChunkDelimiter() {
        return chunkDelimiter == null ? "\n" : chunkDelimiter;
    }

    public String getRetrievalStrategy() {
        return retrievalStrategy == null || retrievalStrategy.isBlank() ? "VECTOR" : retrievalStrategy;
    }

    public Integer getTopK() {
        return topK == null ? 5 : topK;
    }

    public Double getScoreThreshold() {
        return scoreThreshold == null ? 0.0 : scoreThreshold;
    }

    public String getEmbeddingProvider() {
        return embeddingProvider == null || embeddingProvider.isBlank() ? "local" : embeddingProvider;
    }

    public String getEmbeddingModel() {
        return embeddingModel == null || embeddingModel.isBlank() ? "local-hash" : embeddingModel;
    }

    public Integer getEmbeddingDimension() {
        return embeddingDimension == null || embeddingDimension < 4 ? 1024 : embeddingDimension;
    }

    public void update(String name, String description, String status) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void markDeleted() {
        this.status = "DELETED";
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }

    public void updateKnowledgeSettings(Integer chunkSize,
                                        Integer chunkOverlap,
                                        String chunkDelimiter,
                                        String retrievalStrategy,
                                        Integer topK,
                                        Double scoreThreshold,
                                        String embeddingProvider,
                                        String embeddingModel,
                                        Integer embeddingDimension) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.chunkDelimiter = chunkDelimiter;
        this.retrievalStrategy = retrievalStrategy;
        this.topK = topK;
        this.scoreThreshold = scoreThreshold;
        this.embeddingProvider = embeddingProvider;
        this.embeddingModel = embeddingModel;
        this.embeddingDimension = embeddingDimension;
        this.updatedAt = Instant.now();
    }
}
