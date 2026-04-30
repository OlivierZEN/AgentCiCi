package com.codehouse.ciciassistant.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "platform_audit_log")
public class PlatformAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "role_code", nullable = false, length = 32)
    private String roleCode;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "resource_key", nullable = false, length = 128)
    private String resourceKey;

    @Column(name = "detail", nullable = false, length = 4000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlatformAuditLogEntity() {
    }

    public PlatformAuditLogEntity(String orgId,
                                  String userId,
                                  String roleCode,
                                  String eventType,
                                  String resourceType,
                                  String resourceKey,
                                  String detail) {
        this.orgId = orgId;
        this.userId = userId;
        this.roleCode = roleCode;
        this.eventType = eventType;
        this.resourceType = resourceType;
        this.resourceKey = resourceKey;
        this.detail = detail;
        this.createdAt = Instant.now();
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

    public String getRoleCode() {
        return roleCode;
    }

    public String getEventType() {
        return eventType;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceKey() {
        return resourceKey;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
