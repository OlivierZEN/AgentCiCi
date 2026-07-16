package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyActionRepository extends JpaRepository<OntologyActionEntity, Long> {
    List<OntologyActionEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    Optional<OntologyActionEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    Optional<OntologyActionEntity> findByWorkspaceIdAndOrgIdAndKey(Long workspaceId, String orgId, String key);
}
