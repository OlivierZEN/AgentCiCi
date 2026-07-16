package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyRelationRepository extends JpaRepository<OntologyRelationEntity, Long> {
    List<OntologyRelationEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    Optional<OntologyRelationEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    Optional<OntologyRelationEntity> findByWorkspaceIdAndOrgIdAndKey(Long workspaceId, String orgId, String key);
}
