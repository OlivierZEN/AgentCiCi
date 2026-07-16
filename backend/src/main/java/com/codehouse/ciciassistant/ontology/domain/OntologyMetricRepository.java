package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyMetricRepository extends Repository<OntologyMetricEntity, Long> {
    OntologyMetricEntity save(OntologyMetricEntity entity);
    List<OntologyMetricEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    Optional<OntologyMetricEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    Optional<OntologyMetricEntity> findByWorkspaceIdAndOrgIdAndKey(Long workspaceId, String orgId, String key);
    long deleteByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByWorkspaceIdAndOrgId(Long workspaceId, String orgId);
}
