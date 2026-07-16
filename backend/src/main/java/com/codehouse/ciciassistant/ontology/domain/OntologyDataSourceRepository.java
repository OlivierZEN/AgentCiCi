package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyDataSourceRepository extends JpaRepository<OntologyDataSourceEntity, Long> {
    List<OntologyDataSourceEntity> findByWorkspaceIdAndOrgIdOrderByIdAsc(Long workspaceId, String orgId);
    Optional<OntologyDataSourceEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    Optional<OntologyDataSourceEntity> findByWorkspaceIdAndOrgIdAndKey(Long workspaceId, String orgId, String key);
}
