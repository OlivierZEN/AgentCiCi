package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyVersionRepository extends Repository<OntologyVersionEntity, Long> {

    OntologyVersionEntity save(OntologyVersionEntity entity);

    List<OntologyVersionEntity> findByWorkspaceIdAndOrgIdOrderByVersionNoDesc(
            Long workspaceId,
            String orgId);

    Optional<OntologyVersionEntity> findByIdAndWorkspaceIdAndOrgId(
            Long id,
            Long workspaceId,
            String orgId);

    Optional<OntologyVersionEntity> findByWorkspaceIdAndOrgIdAndVersionNo(
            Long workspaceId,
            String orgId,
            Integer versionNo);
}
