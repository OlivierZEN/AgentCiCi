package com.codehouse.ciciassistant.memory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "memory_subject", uniqueConstraints = @UniqueConstraint(
        name = "uq_memory_subject_identity",
        columnNames = {"company_id", "application_code", "subject_type", "external_ref"}))
public class MemorySubjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "application_code", nullable = false, length = 96)
    private String applicationCode;

    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType;

    @Column(name = "external_ref", nullable = false, length = 160)
    private String externalRef;

    @Column(name = "identity_level", nullable = false, length = 32)
    private String identityLevel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MemorySubjectEntity() {
    }

    public MemorySubjectEntity(String companyId, String applicationCode, String subjectType,
                               String externalRef, String identityLevel) {
        this.companyId = companyId;
        this.applicationCode = applicationCode;
        this.subjectType = subjectType;
        this.externalRef = externalRef;
        this.identityLevel = identityLevel;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getApplicationCode() { return applicationCode; }
    public String getSubjectType() { return subjectType; }
    public String getExternalRef() { return externalRef; }
    public String getIdentityLevel() { return identityLevel; }
    public void anonymize() { this.externalRef = "deleted-" + id; this.identityLevel = "ANONYMOUS"; this.updatedAt = Instant.now(); }
}
