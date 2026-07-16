package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyPhysicalFieldRepository extends JpaRepository<OntologyPhysicalFieldEntity, Long> {
    List<OntologyPhysicalFieldEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    List<OntologyPhysicalFieldEntity> findByPhysicalObjectIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
            Long physicalObjectId, Long workspaceId, String orgId);
    Optional<OntologyPhysicalFieldEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
}
