package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyRelationRepository extends Repository<OntologyRelationEntity, Long> {
    List<OntologyRelationEntity> findByWorkspaceIdAndCompanyIdOrderByIdAsc(Long workspaceId, String companyId);
    Optional<OntologyRelationEntity> findByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    Optional<OntologyRelationEntity> findByWorkspaceIdAndCompanyIdAndKey(Long workspaceId, String companyId, String key);
    long deleteByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    long deleteByWorkspaceIdAndCompanyId(Long workspaceId, String companyId);
}
