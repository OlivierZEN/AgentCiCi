package com.codehouse.ciciassistant.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "billing_config_change_log")
public class BillingConfigChangeLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_type", nullable = false, length = 32)
    private String configType;

    @Column(name = "config_code", nullable = false, length = 64)
    private String configCode;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "high_risk", nullable = false)
    private boolean highRisk;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    @Column(name = "actor_role", nullable = false, length = 64)
    private String actorRole;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected BillingConfigChangeLogEntity() {
    }

    public BillingConfigChangeLogEntity(String configType,
                                        String configCode,
                                        int versionNo,
                                        boolean highRisk,
                                        String reason,
                                        String actorId,
                                        String actorRole,
                                        String snapshotJson) {
        this.configType = configType;
        this.configCode = configCode;
        this.versionNo = versionNo;
        this.highRisk = highRisk;
        this.reason = reason;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.snapshotJson = snapshotJson;
    }

    public Long getId() {
        return id;
    }

    public String getConfigType() {
        return configType;
    }

    public String getConfigCode() {
        return configCode;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public boolean isHighRisk() {
        return highRisk;
    }

    public String getReason() {
        return reason;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
