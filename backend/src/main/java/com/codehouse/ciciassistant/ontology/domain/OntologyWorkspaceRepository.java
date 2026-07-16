package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyWorkspaceRepository extends JpaRepository<OntologyWorkspaceEntity, Long> {

    List<OntologyWorkspaceEntity> findByOrgIdOrderByUpdatedAtDesc(String orgId);

    Optional<OntologyWorkspaceEntity> findByIdAndOrgId(Long id, String orgId);

    Optional<OntologyWorkspaceEntity> findByOrgIdAndKey(String orgId, String key);
}
