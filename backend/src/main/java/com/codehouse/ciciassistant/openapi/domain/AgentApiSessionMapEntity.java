package com.codehouse.ciciassistant.openapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_api_session_map")
public class AgentApiSessionMapEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "credential_id", nullable = false)
    private Long credentialId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "external_session_id", nullable = false, length = 160)
    private String externalSessionId;

    @Column(name = "internal_session_id", nullable = false, length = 64)
    private String internalSessionId;

    @Column(name = "external_user_id", length = 128)
    private String externalUserId;

    @Column(name = "conversation_name", length = 160)
    private String conversationName;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentApiSessionMapEntity() {
    }

    public AgentApiSessionMapEntity(String orgId,
                                    Long credentialId,
                                    String agentId,
                                    String externalSessionId,
                                    String internalSessionId,
                                    String externalUserId) {
        this.orgId = orgId;
        this.credentialId = credentialId;
        this.agentId = agentId;
        this.externalSessionId = externalSessionId;
        this.internalSessionId = internalSessionId;
        this.externalUserId = externalUserId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOrgId() { return orgId; }
    public Long getCredentialId() { return credentialId; }
    public String getAgentId() { return agentId; }
    public String getExternalSessionId() { return externalSessionId; }
    public String getInternalSessionId() { return internalSessionId; }
    public String getExternalUserId() { return externalUserId; }
    public String getConversationName() { return conversationName; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateExternalUserId(String externalUserId) {
        this.externalUserId = externalUserId;
        this.updatedAt = Instant.now();
    }

    public void rename(String name) {
        this.conversationName = name;
        this.updatedAt = Instant.now();
    }

    public void markDeleted() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
