package com.codehouse.ciciassistant.kb.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kb_access_grant")
public class KbAccessGrantEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String TARGET_DOCUMENT = "DOCUMENT";
    public static final String TARGET_CHUNK = "CHUNK";
    public static final String PERMISSION_READ = "READ";

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "chunk_id")
    private Long chunkId;

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

    protected KbAccessGrantEntity() {
    }

    public KbAccessGrantEntity(String companyId,
                               Long knowledgeBaseId,
                               String targetType,
                               Long documentId,
                               Long chunkId,
                               String principalType,
                               String principalId,
                               String permission,
                               String source,
                               String grantedBy,
                               Instant expiresAt) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.companyId = companyId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.targetType = targetType;
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.principalType = principalType;
        this.principalId = principalId;
        this.permission = permission == null || permission.isBlank() ? PERMISSION_READ : permission;
        this.source = source == null || source.isBlank() ? "MANUAL" : source;
        this.grantedBy = grantedBy;
        this.expiresAt = expiresAt;
        this.status = STATUS_ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() { return id; }

    public String getCompanyId() { return companyId; }

    public Long getKnowledgeBaseId() { return knowledgeBaseId; }

    public String getTargetType() { return targetType; }

    public Long getDocumentId() { return documentId; }

    public Long getChunkId() { return chunkId; }

    public String getPrincipalType() { return principalType; }

    public String getPrincipalId() { return principalId; }

    public String getPermission() { return permission; }

    public String getSource() { return source; }

    public String getGrantedBy() { return grantedBy; }

    public Instant getExpiresAt() { return expiresAt; }

    public String getStatus() { return status; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isCurrentlyActive(Instant now) {
        return STATUS_ACTIVE.equals(status) && (expiresAt == null || expiresAt.isAfter(now));
    }

    public void revoke() {
        this.status = STATUS_REVOKED;
        this.updatedAt = Instant.now();
    }
}
