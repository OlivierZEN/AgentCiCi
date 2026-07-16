package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyPropertyRepository extends Repository<OntologyPropertyEntity, Long> {
    OntologyPropertyEntity save(OntologyPropertyEntity entity);
    List<OntologyPropertyEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    List<OntologyPropertyEntity> findByConceptIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
            Long conceptId, Long workspaceId, String orgId);
    Optional<OntologyPropertyEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByWorkspaceIdAndOrgId(Long workspaceId, String orgId);
}
