package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyQueryAuditRepository extends JpaRepository<OntologyQueryAuditEntity, Long> {
    List<OntologyQueryAuditEntity> findByWorkspaceIdAndOrgIdOrderByCreatedAtDesc(Long workspaceId, String orgId);
    List<OntologyQueryAuditEntity> findByOrgIdOrderByCreatedAtDesc(String orgId);
    Optional<OntologyQueryAuditEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
}
