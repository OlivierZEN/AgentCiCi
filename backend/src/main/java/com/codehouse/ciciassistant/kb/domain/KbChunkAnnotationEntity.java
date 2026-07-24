package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_chunk_annotation")
public class KbChunkAnnotationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "chunk_id", nullable = false)
    private Long chunkId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "field_key", nullable = false, length = 64)
    private String fieldKey;

    @Column(name = "string_value", nullable = false, length = 1024)
    private String stringValue;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KbChunkAnnotationEntity() {
    }

    public KbChunkAnnotationEntity(String companyId, Long knowledgeBaseId, Long chunkId, Long documentId,
                                   String fieldKey, String stringValue, String source, String createdBy) {
        this.companyId = companyId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.fieldKey = fieldKey;
        this.stringValue = stringValue;
        this.source = source;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public String getCompanyId() { return companyId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public Long getChunkId() { return chunkId; }
    public Long getDocumentId() { return documentId; }
    public String getFieldKey() { return fieldKey; }
    public String getStringValue() { return stringValue; }
    public String getSource() { return source; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateValue(String stringValue, String source, String actor) {
        this.stringValue = stringValue;
        this.source = source;
        this.createdBy = actor;
        this.updatedAt = Instant.now();
    }
}
