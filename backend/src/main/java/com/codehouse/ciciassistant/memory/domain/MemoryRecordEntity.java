package com.codehouse.ciciassistant.memory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "memory_record")
public class MemoryRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "scope", nullable = false, length = 32)
    private String scope;

    @Column(name = "scope_key", length = 160)
    private String scopeKey;

    @Column(name = "memory_type", nullable = false, length = 32)
    private String memoryType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "sensitivity", nullable = false, length = 32)
    private String sensitivity;

    @Column(name = "confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_refs_json", nullable = false, columnDefinition = "TEXT")
    private String sourceRefsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MemoryRecordEntity() {
    }

    public MemoryRecordEntity(String orgId, Long subjectId, String scope, String scopeKey,
                              String memoryType, String content, String status, String sensitivity,
                              BigDecimal confidence, Instant validFrom, Instant validTo,
                              String sourceType, String sourceRefsJson) {
        this.orgId = orgId;
        this.subjectId = subjectId;
        this.scope = scope;
        this.scopeKey = scopeKey;
        this.memoryType = memoryType;
        this.content = content;
        this.status = status;
        this.sensitivity = sensitivity;
        this.confidence = confidence;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.sourceType = sourceType;
        this.sourceRefsJson = sourceRefsJson;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public String getOrgId() { return orgId; }
    public Long getSubjectId() { return subjectId; }
    public String getScope() { return scope; }
    public String getScopeKey() { return scopeKey; }
    public String getMemoryType() { return memoryType; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
    public String getSensitivity() { return sensitivity; }
    public BigDecimal getConfidence() { return confidence; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
    public String getSourceType() { return sourceType; }
    public String getSourceRefsJson() { return sourceRefsJson; }
    public void markExpired() { this.status = "EXPIRED"; this.updatedAt = Instant.now(); }
    public void revokeAndRedact() { this.status = "REVOKED"; this.content = "[deleted]"; this.sourceRefsJson = "[]"; this.updatedAt = Instant.now(); }
}
