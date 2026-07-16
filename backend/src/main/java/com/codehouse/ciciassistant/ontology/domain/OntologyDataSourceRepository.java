package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyDataSourceRepository extends Repository<OntologyDataSourceEntity, Long> {
    OntologyDataSourceEntity save(OntologyDataSourceEntity entity);
    List<OntologyDataSourceEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    Optional<OntologyDataSourceEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    Optional<OntologyDataSourceEntity> findByWorkspaceIdAndOrgIdAndKey(Long workspaceId, String orgId, String key);
    long deleteByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByWorkspaceIdAndOrgId(Long workspaceId, String orgId);
}
