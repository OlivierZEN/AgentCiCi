package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_eval_publish_reference")
public class AgentEvalPublishReferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "eval_run_ids_json", nullable = false, columnDefinition = "TEXT")
    private String evalRunIdsJson;

    @Column(name = "snapshot_fingerprint", length = 128)
    private String snapshotFingerprint;

    @Column(name = "published_by", length = 128)
    private String publishedBy;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    protected AgentEvalPublishReferenceEntity() {
    }

    public AgentEvalPublishReferenceEntity(String orgId,
                                           String agentId,
                                           Integer versionNo,
                                           String evalRunIdsJson,
                                           String snapshotFingerprint,
                                           String publishedBy) {
        this.orgId = orgId;
        this.agentId = agentId;
        this.versionNo = versionNo;
        this.evalRunIdsJson = evalRunIdsJson;
        this.snapshotFingerprint = snapshotFingerprint;
        this.publishedBy = publishedBy;
        this.publishedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOrgId() { return orgId; }
    public String getAgentId() { return agentId; }
    public Integer getVersionNo() { return versionNo; }
    public String getEvalRunIdsJson() { return evalRunIdsJson; }
    public String getSnapshotFingerprint() { return snapshotFingerprint; }
    public String getPublishedBy() { return publishedBy; }
    public Instant getPublishedAt() { return publishedAt; }
}
