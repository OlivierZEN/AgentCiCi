package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ontology_version")
public class OntologyVersionEntity extends AbstractOntologyWorkspaceEntity {

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "source_draft_revision", nullable = false)
    private Long sourceDraftRevision;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "json_schema", nullable = false, columnDefinition = "TEXT")
    private String jsonSchema;

    @Column(name = "graphql_sdl", nullable = false, columnDefinition = "TEXT")
    private String graphqlSdl;

    @Column(name = "query_contract_json", nullable = false, columnDefinition = "TEXT")
    private String queryContractJson;

    @Column(name = "validation_summary_json", nullable = false, columnDefinition = "TEXT")
    private String validationSummaryJson;

    @Column(name = "published_by", nullable = false, length = 64)
    private String publishedBy;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    protected OntologyVersionEntity() {
    }

    public OntologyVersionEntity(
            String companyId,
            Long workspaceId,
            Integer versionNo,
            Long sourceDraftRevision,
            String contentHash,
            String snapshotJson,
            String jsonSchema,
            String graphqlSdl,
            String queryContractJson,
            String validationSummaryJson,
            String publishedBy) {
        super(companyId, workspaceId);
        this.versionNo = versionNo;
        this.sourceDraftRevision = sourceDraftRevision;
        this.contentHash = contentHash;
        this.snapshotJson = snapshotJson;
        this.jsonSchema = jsonSchema;
        this.graphqlSdl = graphqlSdl;
        this.queryContractJson = queryContractJson;
        this.validationSummaryJson = validationSummaryJson;
        this.publishedBy = publishedBy;
        this.publishedAt = Instant.now();
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public Long getSourceDraftRevision() {
        return sourceDraftRevision;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public String getJsonSchema() {
        return jsonSchema;
    }

    public String getGraphqlSdl() {
        return graphqlSdl;
    }

    public String getQueryContractJson() {
        return queryContractJson;
    }

    public String getValidationSummaryJson() {
        return validationSummaryJson;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
