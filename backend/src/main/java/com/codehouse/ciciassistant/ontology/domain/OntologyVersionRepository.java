package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyVersionRepository extends Repository<OntologyVersionEntity, Long> {

    List<OntologyVersionEntity> findByWorkspaceIdAndCompanyIdOrderByVersionNoDesc(
            Long workspaceId,
            String companyId);

    Optional<OntologyVersionEntity> findByIdAndWorkspaceIdAndCompanyId(
            Long id,
            Long workspaceId,
            String companyId);

    Optional<OntologyVersionEntity> findByWorkspaceIdAndCompanyIdAndVersionNo(
            Long workspaceId,
            String companyId,
            Integer versionNo);

    Optional<OntologyVersionEntity> findByWorkspaceIdAndCompanyIdAndSourceDraftRevision(
            Long workspaceId,
            String companyId,
            Long sourceDraftRevision);
}
