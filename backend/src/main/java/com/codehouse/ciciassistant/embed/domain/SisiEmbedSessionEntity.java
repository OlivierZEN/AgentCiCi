package com.codehouse.ciciassistant.embed.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "sisi_embed_session")
public class SisiEmbedSessionEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "app_code", nullable = false, length = 64)
    private String appCode;

    @Column(name = "chat_session_id", nullable = false, length = 64)
    private String chatSessionId;

    @Column(name = "internal_user_id", nullable = false, length = 64)
    private String internalUserId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "external_tenant_id", nullable = false, length = 128)
    private String externalTenantId;

    @Column(name = "external_user_id", nullable = false, length = 128)
    private String externalUserId;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "object_type", nullable = false, length = 96)
    private String objectType;

    @Column(name = "object_id", nullable = false, length = 160)
    private String objectId;

    @Column(name = "record_name", length = 256)
    private String recordName;

    @Column(name = "customer_name", length = 256)
    private String customerName;

    @Column(name = "parent_origin", nullable = false, length = 256)
    private String parentOrigin;

    @Column(name = "context_json", nullable = false, columnDefinition = "TEXT")
    private String contextJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SisiEmbedSessionEntity() {
    }

    public SisiEmbedSessionEntity(String id,
                                  String companyId,
                                  String chatSessionId,
                                  String internalUserId,
                                  String agentId,
                                  String externalTenantId,
                                  String externalUserId,
                                  String source,
                                  String objectType,
                                  String objectId,
                                  String recordName,
                                  String customerName,
                                  String parentOrigin,
                                  String contextJson) {
        this.id = id;
        this.companyId = companyId;
        this.appCode = "sisi";
        this.chatSessionId = chatSessionId;
        this.internalUserId = internalUserId;
        this.agentId = agentId;
        this.externalTenantId = externalTenantId;
        this.externalUserId = externalUserId;
        this.source = source;
        this.objectType = objectType;
        this.objectId = objectId;
        this.recordName = recordName;
        this.customerName = customerName;
        this.parentOrigin = parentOrigin;
        this.contextJson = contextJson;
        this.status = STATUS_ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getAppCode() { return appCode; }
    public String getChatSessionId() { return chatSessionId; }
    public String getInternalUserId() { return internalUserId; }
    public String getAgentId() { return agentId; }
    public String getExternalTenantId() { return externalTenantId; }
    public String getExternalUserId() { return externalUserId; }
    public String getSource() { return source; }
    public String getObjectType() { return objectType; }
    public String getObjectId() { return objectId; }
    public String getRecordName() { return recordName; }
    public String getCustomerName() { return customerName; }
    public String getParentOrigin() { return parentOrigin; }
    public String getContextJson() { return contextJson; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void touch(String parentOrigin, String contextJson, String recordName, String customerName) {
        this.parentOrigin = parentOrigin;
        this.contextJson = contextJson;
        this.recordName = recordName;
        this.customerName = customerName;
        this.updatedAt = Instant.now();
    }
}
