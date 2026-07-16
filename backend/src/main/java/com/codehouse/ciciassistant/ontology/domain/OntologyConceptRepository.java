package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyConceptRepository extends JpaRepository<OntologyConceptEntity, Long> {
    List<OntologyConceptEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    Optional<OntologyConceptEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    Optional<OntologyConceptEntity> findByWorkspaceIdAndOrgIdAndKey(Long workspaceId, String orgId, String key);
}
