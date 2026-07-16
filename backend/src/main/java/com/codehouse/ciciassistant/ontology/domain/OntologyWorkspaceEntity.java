package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ontology_workspace")
public class OntologyWorkspaceEntity implements OntologyTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "key", nullable = false, length = 128)
    private String key;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "draft_revision", nullable = false)
    private Long draftRevision;

    @Column(name = "published_version")
    private Integer publishedVersion;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OntologyWorkspaceEntity() {
    }

    public OntologyWorkspaceEntity(
            String orgId,
            String key,
            String name,
            String description,
            String createdBy) {
        this.orgId = orgId;
        this.key = key;
        this.name = name;
        this.description = description;
        this.status = "DRAFT";
        this.draftRevision = 0L;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getKey() {
        return key;
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

    public Long getDraftRevision() {
        return draftRevision;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void applyDraftMetadata(
            String name,
            String description,
            String updatedBy) {
        this.name = name;
        this.description = description;
        this.status = "DRAFT";
        this.draftRevision = this.draftRevision == null ? 1L : this.draftRevision + 1L;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public void updateMetadata(String name, String description, String updatedBy) {
        this.name = name;
        this.description = description;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public void archive(String updatedBy) {
        this.status = "ARCHIVED";
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public void advanceDraftRevision(String updatedBy) {
        this.status = "DRAFT";
        this.draftRevision = this.draftRevision == null ? 1L : this.draftRevision + 1L;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public void markPublished(Integer version, String updatedBy) {
        this.status = "PUBLISHED";
        this.publishedVersion = version;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
}
