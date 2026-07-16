package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyWorkspaceRepository extends Repository<OntologyWorkspaceEntity, Long> {

    OntologyWorkspaceEntity save(OntologyWorkspaceEntity entity);

    List<OntologyWorkspaceEntity> findByOrgIdOrderByUpdatedAtDesc(String orgId);

    Optional<OntologyWorkspaceEntity> findByIdAndOrgId(Long id, String orgId);

    Optional<OntologyWorkspaceEntity> findByOrgIdAndKey(String orgId, String key);

    long deleteByIdAndOrgId(Long id, String orgId);
}
