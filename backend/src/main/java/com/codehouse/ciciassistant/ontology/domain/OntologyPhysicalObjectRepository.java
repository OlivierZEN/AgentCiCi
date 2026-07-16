package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyPhysicalObjectRepository extends JpaRepository<OntologyPhysicalObjectEntity, Long> {
    List<OntologyPhysicalObjectEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    List<OntologyPhysicalObjectEntity> findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
            Long dataSourceId, Long workspaceId, String orgId);
    Optional<OntologyPhysicalObjectEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
}
