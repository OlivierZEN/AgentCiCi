package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyPhysicalFieldRepository extends Repository<OntologyPhysicalFieldEntity, Long> {
    List<OntologyPhysicalFieldEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    List<OntologyPhysicalFieldEntity> findByPhysicalObjectIdAndWorkspaceIdAndOrgIdOrderByIdAsc(
            Long physicalObjectId, Long workspaceId, String orgId);
    Optional<OntologyPhysicalFieldEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByWorkspaceIdAndOrgId(Long workspaceId, String orgId);
}
