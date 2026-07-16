package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyQueryAuditRepository extends Repository<OntologyQueryAuditEntity, Long> {
    List<OntologyQueryAuditEntity> findByWorkspaceIdAndOrgIdOrderByCreatedAtDesc(Long workspaceId, String orgId);
    List<OntologyQueryAuditEntity> findByOrgIdOrderByCreatedAtDesc(String orgId);
    Optional<OntologyQueryAuditEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByWorkspaceIdAndOrgId(Long workspaceId, String orgId);
}
