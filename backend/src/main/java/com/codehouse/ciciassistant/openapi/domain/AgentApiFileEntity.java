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

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

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

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_host", length = 255)
    private String sourceHost;

    @Column(name = "source_url_hash", length = 64)
    private String sourceUrlHash;

    @Column(name = "detected_mime_type", length = 128)
    private String detectedMimeType;

    @Column(name = "file_kind", length = 32)
    private String fileKind;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "import_idempotency_key_hash", length = 64)
    private String importIdempotencyKeyHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentApiFileEntity() {
    }

    public AgentApiFileEntity(String fileId,
                              String companyId,
                              Long credentialId,
                              String agentId,
                              String externalUserId,
                              String externalSessionId,
                              String name,
                              long sizeBytes,
                              String mimeType,
                              String storageKey,
                              String sourceType,
                              String sourceHost,
                              String sourceUrlHash,
                              String detectedMimeType,
                              String fileKind,
                              String sha256,
                              String importIdempotencyKeyHash,
                              Instant expiresAt) {
        this.fileId = fileId;
        this.companyId = companyId;
        this.credentialId = credentialId;
        this.agentId = agentId;
        this.externalUserId = externalUserId;
        this.externalSessionId = externalSessionId;
        this.name = name;
        this.sizeBytes = sizeBytes;
        this.mimeType = mimeType;
        this.storageKey = storageKey;
        this.sourceType = sourceType;
        this.sourceHost = sourceHost;
        this.sourceUrlHash = sourceUrlHash;
        this.detectedMimeType = detectedMimeType;
        this.fileKind = fileKind;
        this.sha256 = sha256;
        this.status = "READY";
        this.failureCode = "";
        this.importIdempotencyKeyHash = importIdempotencyKeyHash;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public String getFileId() { return fileId; }
    public String getExternalUserId() { return externalUserId; }
    public String getExternalSessionId() { return externalSessionId; }
    public String getName() { return name; }
    public long getSizeBytes() { return sizeBytes; }
    public String getMimeType() { return mimeType; }
    public String getStorageKey() { return storageKey; }
    public String getCompanyId() { return companyId; }
    public Long getCredentialId() { return credentialId; }
    public String getAgentId() { return agentId; }
    public String getSourceType() { return sourceType; }
    public String getSourceHost() { return sourceHost; }
    public String getSourceUrlHash() { return sourceUrlHash; }
    public String getDetectedMimeType() { return detectedMimeType; }
    public String getFileKind() { return fileKind; }
    public String getSha256() { return sha256; }
    public String getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public String getImportIdempotencyKeyHash() { return importIdempotencyKeyHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void bindExternalSession(String externalSessionId) {
        if (this.externalSessionId == null || this.externalSessionId.isBlank()) {
            this.externalSessionId = externalSessionId;
        }
    }

    public void bindExternalUser(String externalUserId) {
        if (this.externalUserId == null || this.externalUserId.isBlank()) {
            this.externalUserId = externalUserId;
        }
    }
}
