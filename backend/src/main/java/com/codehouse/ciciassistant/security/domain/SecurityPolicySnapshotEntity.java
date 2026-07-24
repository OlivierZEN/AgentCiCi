package com.codehouse.ciciassistant.security.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "security_policy_snapshot")
public class SecurityPolicySnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "policy_version", nullable = false, length = 64)
    private String policyVersion;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SecurityPolicySnapshotEntity() {
    }

    public SecurityPolicySnapshotEntity(String companyId, String policyVersion, String snapshotJson) {
        this.companyId = companyId;
        this.policyVersion = policyVersion;
        this.snapshotJson = snapshotJson;
        this.createdAt = Instant.now();
    }
}
