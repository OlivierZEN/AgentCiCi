package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_permission_audit")
public class AgentPermissionAuditEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "actor_user_id", length = 64)
    private String actorUserId;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "target_principal_type", length = 32)
    private String targetPrincipalType;

    @Column(name = "target_principal_id", length = 128)
    private String targetPrincipalId;

    @Column(name = "permission", length = 32)
    private String permission;

    @Column(name = "before_json", columnDefinition = "TEXT")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "TEXT")
    private String afterJson;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentPermissionAuditEntity() {
    }

    public AgentPermissionAuditEntity(String companyId,
                                      String agentId,
                                      String actorUserId,
                                      String action,
                                      String targetPrincipalType,
                                      String targetPrincipalId,
                                      String permission,
                                      String beforeJson,
                                      String afterJson,
                                      String reason,
                                      String traceId) {
        this.id = UUID.randomUUID().toString();
        this.companyId = companyId;
        this.agentId = agentId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetPrincipalType = targetPrincipalType;
        this.targetPrincipalId = targetPrincipalId;
        this.permission = permission;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.reason = reason;
        this.traceId = traceId;
        this.createdAt = Instant.now();
    }
}
