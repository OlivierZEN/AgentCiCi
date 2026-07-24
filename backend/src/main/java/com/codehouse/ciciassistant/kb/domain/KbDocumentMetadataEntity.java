package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_document_metadata")
public class KbDocumentMetadataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "field_key", nullable = false, length = 64)
    private String fieldKey;

    @Column(name = "string_value", nullable = false, length = 1024)
    private String stringValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KbDocumentMetadataEntity() {
    }

    public KbDocumentMetadataEntity(String companyId, Long knowledgeBaseId, Long documentId, String fieldKey, String stringValue) {
        this.companyId = companyId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.documentId = documentId;
        this.fieldKey = fieldKey;
        this.stringValue = stringValue;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
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

    public Long getDocumentId() {
        return documentId;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
        this.updatedAt = Instant.now();
    }
}
