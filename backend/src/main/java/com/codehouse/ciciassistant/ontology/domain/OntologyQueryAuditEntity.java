package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ontology_query_audit")
public class OntologyQueryAuditEntity extends AbstractOntologyWorkspaceEntity {

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "data_source_id")
    private Long dataSourceId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "concept_key", nullable = false, length = 128)
    private String conceptKey;

    @Column(name = "query_json", nullable = false, columnDefinition = "TEXT")
    private String queryJson;

    @Column(name = "result_count", nullable = false)
    private Integer resultCount;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "sensitive_values_redacted", nullable = false)
    private boolean sensitiveValuesRedacted;

    protected OntologyQueryAuditEntity() {
    }

    public OntologyQueryAuditEntity(
            String orgId,
            Long workspaceId,
            Long versionId,
            Long dataSourceId,
            String userId,
            String conceptKey,
            String queryJson,
            Integer resultCount,
            Long durationMs,
            String status,
            String evidenceJson,
            String errorCode) {
        super(orgId, workspaceId);
        this.versionId = versionId;
        this.dataSourceId = dataSourceId;
        this.userId = userId;
        this.conceptKey = conceptKey;
        this.queryJson = queryJson;
        this.resultCount = resultCount;
        this.durationMs = durationMs;
        this.status = status;
        this.evidenceJson = evidenceJson;
        this.errorCode = errorCode;
        this.sensitiveValuesRedacted = true;
    }

    public Long getVersionId() { return versionId; }
    public Long getDataSourceId() { return dataSourceId; }
    public String getUserId() { return userId; }
    public String getConceptKey() { return conceptKey; }
    public String getQueryJson() { return queryJson; }
    public Integer getResultCount() { return resultCount; }
    public Long getDurationMs() { return durationMs; }
    public String getStatus() { return status; }
    public String getEvidenceJson() { return evidenceJson; }
    public String getErrorCode() { return errorCode; }
    public boolean isSensitiveValuesRedacted() { return sensitiveValuesRedacted; }
}
