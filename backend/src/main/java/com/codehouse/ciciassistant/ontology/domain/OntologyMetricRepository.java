package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyMetricRepository extends JpaRepository<OntologyMetricEntity, Long> {
    List<OntologyMetricEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    Optional<OntologyMetricEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    Optional<OntologyMetricEntity> findByWorkspaceIdAndOrgIdAndKey(Long workspaceId, String orgId, String key);
}
