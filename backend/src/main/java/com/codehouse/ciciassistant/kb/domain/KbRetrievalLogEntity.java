package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_retrieval_log")
public class KbRetrievalLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "query", nullable = false, length = 2000)
    private String query;

    @Column(name = "retrieval_strategy", nullable = false, length = 32)
    private String retrievalStrategy;

    @Column(name = "top_k", nullable = false)
    private Integer topK;

    @Column(name = "score_threshold", nullable = false)
    private Double scoreThreshold;

    @Column(name = "hit_count", nullable = false)
    private Integer hitCount;

    @Column(name = "hit_summary_json", nullable = false, columnDefinition = "TEXT")
    private String hitSummaryJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KbRetrievalLogEntity() {
    }

    public KbRetrievalLogEntity(String orgId,
                                Long knowledgeBaseId,
                                String query,
                                String retrievalStrategy,
                                Integer topK,
                                Double scoreThreshold,
                                Integer hitCount,
                                String hitSummaryJson) {
        this.orgId = orgId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.query = query;
        this.retrievalStrategy = retrievalStrategy;
        this.topK = topK;
        this.scoreThreshold = scoreThreshold;
        this.hitCount = hitCount;
        this.hitSummaryJson = hitSummaryJson;
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

    public String getQuery() {
        return query;
    }

    public String getRetrievalStrategy() {
        return retrievalStrategy;
    }

    public Integer getTopK() {
        return topK;
    }

    public Double getScoreThreshold() {
        return scoreThreshold;
    }

    public Integer getHitCount() {
        return hitCount;
    }

    public String getHitSummaryJson() {
        return hitSummaryJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
