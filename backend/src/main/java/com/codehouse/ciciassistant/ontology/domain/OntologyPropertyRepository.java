package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyPropertyRepository extends JpaRepository<OntologyPropertyEntity, Long> {
    List<OntologyPropertyEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    List<OntologyPropertyEntity> findByConceptIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
            Long conceptId, Long workspaceId, String orgId);
    Optional<OntologyPropertyEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
}
