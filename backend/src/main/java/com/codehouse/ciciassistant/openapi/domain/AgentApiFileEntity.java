package com.codehouse.ciciassistant.openapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_api_file")
public class AgentApiFileEntity {

    @Id
    @Column(name = "file_id", nullable = false, length = 64)
    private String fileId;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "credential_id", nullable = false)
    private Long credentialId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "external_user_id", length = 128)
    private String externalUserId;

    @Column(name = "external_session_id", length = 160)
    private String externalSessionId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentApiFileEntity() {
    }

    public AgentApiFileEntity(String fileId,
                              String orgId,
                              Long credentialId,
                              String agentId,
                              String externalUserId,
                              String externalSessionId,
                              String name,
                              long sizeBytes,
                              String mimeType,
                              String storageKey) {
        this.fileId = fileId;
        this.orgId = orgId;
        this.credentialId = credentialId;
        this.agentId = agentId;
        this.externalUserId = externalUserId;
        this.externalSessionId = externalSessionId;
        this.name = name;
        this.sizeBytes = sizeBytes;
        this.mimeType = mimeType;
        this.storageKey = storageKey;
        this.createdAt = Instant.now();
    }

    public String getFileId() { return fileId; }
    public String getExternalUserId() { return externalUserId; }
    public String getExternalSessionId() { return externalSessionId; }
    public String getName() { return name; }
    public long getSizeBytes() { return sizeBytes; }
    public String getMimeType() { return mimeType; }
    public String getStorageKey() { return storageKey; }
    public Instant getCreatedAt() { return createdAt; }
}
