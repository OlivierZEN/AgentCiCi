package com.codehouse.ciciassistant.userworkflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_workflow_spec")
public class UserWorkflowSpecEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "source_text", nullable = false, columnDefinition = "TEXT")
    private String sourceText;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "draft_version_no")
    private Integer draftVersionNo;

    @Column(name = "published_version_id")
    private Long publishedVersionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserWorkflowSpecEntity() {
    }

    public UserWorkflowSpecEntity(String orgId, String userId, String agentId, String sourceText) {
        this.orgId = orgId;
        this.userId = userId;
        this.agentId = agentId;
        this.sourceText = sourceText;
        this.status = "DRAFT";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getUserId() {
        return userId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getSourceText() {
        return sourceText;
    }

    public String getStatus() {
        return status;
    }

    public Integer getDraftVersionNo() {
        return draftVersionNo;
    }

    public Long getPublishedVersionId() {
        return publishedVersionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateSourceText(String sourceText) {
        this.sourceText = sourceText;
        this.status = "DRAFT";
        this.updatedAt = Instant.now();
    }

    public void markCompiled(Integer draftVersionNo) {
        this.draftVersionNo = draftVersionNo;
        this.status = "COMPILED";
        this.updatedAt = Instant.now();
    }

    public void markPublished(Long publishedVersionId) {
        this.publishedVersionId = publishedVersionId;
        this.status = "PUBLISHED";
        this.updatedAt = Instant.now();
    }
}
