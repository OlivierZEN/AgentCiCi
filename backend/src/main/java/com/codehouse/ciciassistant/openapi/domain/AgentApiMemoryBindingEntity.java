package com.codehouse.ciciassistant.openapi.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "agent_api_memory_binding")
public class AgentApiMemoryBindingEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "credential_id", nullable = false, unique = true) private Long credentialId;
    @Column(name = "application_code", nullable = false) private String applicationCode;
    @Column(name = "subject_type", nullable = false) private String subjectType;
    @Column(name = "identity_level", nullable = false) private String identityLevel;
    @Column(name = "domain_namespaces_json", nullable = false, columnDefinition = "TEXT") private String domainNamespacesJson;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected AgentApiMemoryBindingEntity() {}
    public AgentApiMemoryBindingEntity(Long credentialId, String applicationCode, String subjectType, String identityLevel, String namespaces) { this.credentialId=credentialId; this.applicationCode=applicationCode; this.subjectType=subjectType; this.identityLevel=identityLevel; this.domainNamespacesJson=namespaces; this.enabled=true; this.createdAt=Instant.now(); this.updatedAt=createdAt; }
    public Long getCredentialId(){return credentialId;} public String getApplicationCode(){return applicationCode;} public String getSubjectType(){return subjectType;} public String getIdentityLevel(){return identityLevel;} public String getDomainNamespacesJson(){return domainNamespacesJson;} public boolean isEnabled(){return enabled;}
}
