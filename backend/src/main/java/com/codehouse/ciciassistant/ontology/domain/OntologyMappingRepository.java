package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyMappingRepository extends Repository<OntologyMappingEntity, Long> {
    List<OntologyMappingEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    Optional<OntologyMappingEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    List<OntologyMappingEntity> findByWorkspaceIdAndOrgIdAndTargetTypeAndTargetKey(
            Long workspaceId, String orgId, String targetType, String targetKey);
    Optional<OntologyMappingEntity> findByWorkspaceIdAndOrgIdAndTargetTypeAndTargetKeyAndDataSourceId(
            Long workspaceId, String orgId, String targetType, String targetKey, Long dataSourceId);
    long deleteByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByWorkspaceIdAndOrgId(Long workspaceId, String orgId);
}
