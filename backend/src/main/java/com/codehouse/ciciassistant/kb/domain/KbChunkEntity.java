package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    protected KbChunkEntity() {
    }

    public KbChunkEntity(String orgId, String knowledgeBaseId, String content, String tags) {
        this.orgId = orgId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.content = content;
        this.tags = tags;
    }

    public KbChunkEntity(String orgId, String knowledgeBaseId, String content, String tags, String vectorId) {
        this.orgId = orgId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.content = content;
        this.tags = tags;
        this.vectorId = vectorId;
    }

    public String getContent() {
        return content;
    }

    public String getVectorId() {
        return vectorId;
    }
}
