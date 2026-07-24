package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_metadata_field")
public class KbMetadataFieldEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "field_key", nullable = false, length = 64)
    private String fieldKey;

    @Column(name = "field_name", nullable = false, length = 128)
    private String fieldName;

    @Column(name = "value_type", nullable = false, length = 16)
    private String valueType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KbMetadataFieldEntity() {
    }

    public KbMetadataFieldEntity(String companyId, Long knowledgeBaseId, String fieldKey, String fieldName, String valueType) {
        this.companyId = companyId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.fieldKey = fieldKey;
        this.fieldName = fieldName;
        this.valueType = valueType;
        this.createdAt = Instant.now();
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

    public String getFieldKey() {
        return fieldKey;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getValueType() {
        return valueType;
    }
}
