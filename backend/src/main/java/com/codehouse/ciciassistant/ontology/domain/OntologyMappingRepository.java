package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyMappingRepository extends Repository<OntologyMappingEntity, Long> {
    List<OntologyMappingEntity> findByWorkspaceIdAndCompanyIdOrderByIdAsc(Long workspaceId, String companyId);
    Optional<OntologyMappingEntity> findByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    List<OntologyMappingEntity> findByWorkspaceIdAndCompanyIdAndTargetTypeAndTargetKey(
            Long workspaceId, String companyId, String targetType, String targetKey);
    Optional<OntologyMappingEntity> findByWorkspaceIdAndCompanyIdAndTargetTypeAndTargetKeyAndDataSourceId(
            Long workspaceId, String companyId, String targetType, String targetKey, Long dataSourceId);
    long deleteByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    long deleteByWorkspaceIdAndCompanyId(Long workspaceId, String companyId);
}
