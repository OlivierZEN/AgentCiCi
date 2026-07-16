package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyPhysicalObjectRepository extends Repository<OntologyPhysicalObjectEntity, Long> {
    OntologyPhysicalObjectEntity save(OntologyPhysicalObjectEntity entity);
    List<OntologyPhysicalObjectEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    List<OntologyPhysicalObjectEntity> findByDataSourceIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
            Long dataSourceId, Long workspaceId, String orgId);
    Optional<OntologyPhysicalObjectEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByWorkspaceIdAndOrgId(Long workspaceId, String orgId);
}
