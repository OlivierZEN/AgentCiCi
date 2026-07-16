package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyMappingRepository extends JpaRepository<OntologyMappingEntity, Long> {
    List<OntologyMappingEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    Optional<OntologyMappingEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    List<OntologyMappingEntity> findByWorkspaceIdAndOrgIdAndTargetTypeAndTargetKey(
            Long workspaceId, String orgId, String targetType, String targetKey);
}
