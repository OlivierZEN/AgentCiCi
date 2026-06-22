package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kb_source_document_map")
public class KbSourceDocumentMapEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;

    @Column(name = "external_document_id", nullable = false, length = 256)
    private String externalDocumentId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "source_hash", length = 128)
    private String sourceHash;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KbSourceDocumentMapEntity() {
    }

    public KbSourceDocumentMapEntity(String orgId,
                                     Long knowledgeBaseId,
                                     Long dataSourceId,
                                     String externalDocumentId,
                                     Long documentId,
                                     String sourceHash) {
        Instant now = Instant.now();
        this.orgId = orgId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.dataSourceId = dataSourceId;
        this.externalDocumentId = externalDocumentId;
        this.documentId = documentId;
        this.sourceHash = sourceHash;
        this.lastSyncedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(Long documentId, String sourceHash) {
        this.documentId = documentId;
        this.sourceHash = sourceHash;
        this.lastSyncedAt = Instant.now();
        this.updatedAt = this.lastSyncedAt;
    }

    public Long getId() { return id; }

    public String getOrgId() { return orgId; }

    public Long getKnowledgeBaseId() { return knowledgeBaseId; }

    public Long getDataSourceId() { return dataSourceId; }

    public String getExternalDocumentId() { return externalDocumentId; }

    public Long getDocumentId() { return documentId; }

    public String getSourceHash() { return sourceHash; }

    public Instant getLastSyncedAt() { return lastSyncedAt; }
}
