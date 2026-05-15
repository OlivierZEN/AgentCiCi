package com.codehouse.ciciassistant.customerinsight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_insight_source_snapshot")
public class CustomerInsightSourceSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_key", nullable = false, length = 256)
    private String sourceKey;

    @Column(name = "source_label", nullable = false, length = 256)
    private String sourceLabel;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    protected CustomerInsightSourceSnapshotEntity() {
    }

    public CustomerInsightSourceSnapshotEntity(Long projectId,
                                               String sourceType,
                                               String sourceKey,
                                               String sourceLabel,
                                               String snapshotJson) {
        this.projectId = projectId;
        this.sourceType = sourceType;
        this.sourceKey = sourceKey;
        this.sourceLabel = sourceLabel;
        this.snapshotJson = snapshotJson == null || snapshotJson.isBlank() ? "{}" : snapshotJson;
        this.collectedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getSourceType() { return sourceType; }
    public String getSourceKey() { return sourceKey; }
    public String getSourceLabel() { return sourceLabel; }
    public String getSnapshotJson() { return snapshotJson; }
    public Instant getCollectedAt() { return collectedAt; }
}
