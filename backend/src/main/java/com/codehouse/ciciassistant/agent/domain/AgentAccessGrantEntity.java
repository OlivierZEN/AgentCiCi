package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_access_grant")
public class AgentAccessGrantEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "principal_type", nullable = false, length = 32)
    private String principalType;

    @Column(name = "principal_id", length = 128)
    private String principalId;

    @Column(name = "permission", nullable = false, length = 32)
    private String permission;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "granted_by", length = 64)
    private String grantedBy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentAccessGrantEntity() {
    }

    public AgentAccessGrantEntity(String companyId,
                                  String agentId,
                                  String principalType,
                                  String principalId,
                                  String permission,
                                  String source,
                                  String grantedBy,
                                  Instant expiresAt) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.companyId = companyId;
        this.agentId = agentId;
        this.principalType = principalType;
        this.principalId = principalId;
        this.permission = permission;
        this.source = source == null || source.isBlank() ? "MANUAL" : source;
        this.grantedBy = grantedBy;
        this.expiresAt = expiresAt;
        this.status = STATUS_ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getPrincipalType() {
        return principalType;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public String getPermission() {
        return permission;
    }

    public String getSource() {
        return source;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isCurrentlyActive(Instant now) {
        return STATUS_ACTIVE.equals(status) && (expiresAt == null || expiresAt.isAfter(now));
    }

    public void revoke() {
        this.status = STATUS_REVOKED;
        this.updatedAt = Instant.now();
    }
}
