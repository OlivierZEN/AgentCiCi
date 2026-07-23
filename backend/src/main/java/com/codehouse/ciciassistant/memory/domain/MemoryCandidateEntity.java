package com.codehouse.ciciassistant.memory.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "memory_candidate")
public class MemoryCandidateEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "org_id", nullable = false) private String orgId;
    @Column(name = "subject_id", nullable = false) private Long subjectId;
    @Column(nullable = false) private String scope;
    @Column(name = "scope_key") private String scopeKey;
    @Column(name = "memory_type", nullable = false) private String memoryType;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(nullable = false) private String sensitivity;
    @Column(nullable = false, precision = 3, scale = 2) private BigDecimal confidence;
    @Column(name = "valid_from", nullable = false) private Instant validFrom;
    @Column(name = "valid_to") private Instant validTo;
    @Column(name = "source_type", nullable = false) private String sourceType;
    @Column(name = "source_refs_json", nullable = false, columnDefinition = "TEXT") private String sourceRefsJson;
    @Column(nullable = false) private String status;
    @Column(name = "reviewed_by") private String reviewedBy;
    @Column(name = "review_reason", columnDefinition = "TEXT") private String reviewReason;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected MemoryCandidateEntity() {}
    public MemoryCandidateEntity(String orgId, Long subjectId, String scope, String scopeKey, String memoryType, String content, String sensitivity, BigDecimal confidence, Instant validFrom, Instant validTo, String sourceType, String sourceRefsJson) {
        this.orgId=orgId; this.subjectId=subjectId; this.scope=scope; this.scopeKey=scopeKey; this.memoryType=memoryType; this.content=content; this.sensitivity=sensitivity; this.confidence=confidence; this.validFrom=validFrom; this.validTo=validTo; this.sourceType=sourceType; this.sourceRefsJson=sourceRefsJson; this.status="PENDING"; this.createdAt=Instant.now(); this.updatedAt=createdAt;
    }
    public void review(String status, String reviewer, String reason) { this.status=status; this.reviewedBy=reviewer; this.reviewReason=reason; this.reviewedAt=Instant.now(); this.updatedAt=reviewedAt; }
    public void revokeAndRedact() { this.status="REJECTED"; this.content="[deleted]"; this.sourceRefsJson="[]"; this.reviewReason="subject deletion"; this.reviewedAt=Instant.now(); this.updatedAt=reviewedAt; }
    public Long getId(){return id;} public String getOrgId(){return orgId;} public Long getSubjectId(){return subjectId;} public String getScope(){return scope;} public String getScopeKey(){return scopeKey;} public String getMemoryType(){return memoryType;} public String getContent(){return content;} public String getSensitivity(){return sensitivity;} public BigDecimal getConfidence(){return confidence;} public Instant getValidFrom(){return validFrom;} public Instant getValidTo(){return validTo;} public String getSourceType(){return sourceType;} public String getSourceRefsJson(){return sourceRefsJson;} public String getStatus(){return status;}
}
