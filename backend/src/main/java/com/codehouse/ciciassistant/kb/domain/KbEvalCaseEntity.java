package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_eval_case")
public class KbEvalCaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "suite_id", nullable = false)
    private Long suiteId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "query", nullable = false, columnDefinition = "TEXT")
    private String query;

    @Column(name = "expected_document_id")
    private Long expectedDocumentId;

    @Column(name = "expected_document_keyword", length = 256)
    private String expectedDocumentKeyword;

    @Column(name = "expected_chunk_keyword", length = 512)
    private String expectedChunkKeyword;

    @Column(name = "min_score")
    private Double minScore;

    @Column(name = "forbidden_document_id")
    private Long forbiddenDocumentId;

    @Column(name = "metadata_filters_json", columnDefinition = "TEXT")
    private String metadataFiltersJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KbEvalCaseEntity() {
    }

    public KbEvalCaseEntity(String companyId,
                            Long suiteId,
                            Long knowledgeBaseId,
                            String query,
                            Long expectedDocumentId,
                            String expectedDocumentKeyword,
                            String expectedChunkKeyword,
                            Double minScore,
                            Long forbiddenDocumentId,
                            String metadataFiltersJson) {
        Instant now = Instant.now();
        this.companyId = companyId;
        this.suiteId = suiteId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.query = query;
        this.expectedDocumentId = expectedDocumentId;
        this.expectedDocumentKeyword = expectedDocumentKeyword;
        this.expectedChunkKeyword = expectedChunkKeyword;
        this.minScore = minScore;
        this.forbiddenDocumentId = forbiddenDocumentId;
        this.metadataFiltersJson = metadataFiltersJson;
        this.status = STATUS_ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }

    public String getCompanyId() { return companyId; }

    public Long getSuiteId() { return suiteId; }

    public Long getKnowledgeBaseId() { return knowledgeBaseId; }

    public String getQuery() { return query; }

    public Long getExpectedDocumentId() { return expectedDocumentId; }

    public String getExpectedDocumentKeyword() { return expectedDocumentKeyword; }

    public String getExpectedChunkKeyword() { return expectedChunkKeyword; }

    public Double getMinScore() { return minScore; }

    public Long getForbiddenDocumentId() { return forbiddenDocumentId; }

    public String getMetadataFiltersJson() { return metadataFiltersJson; }

    public String getStatus() { return status; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
