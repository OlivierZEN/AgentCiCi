package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyPropertyRepository extends Repository<OntologyPropertyEntity, Long> {
    List<OntologyPropertyEntity> findByWorkspaceIdAndCompanyIdOrderByIdAsc(Long workspaceId, String companyId);
    List<OntologyPropertyEntity> findByConceptIdAndWorkspaceIdAndCompanyIdOrderByIdAsc(
            Long conceptId, Long workspaceId, String companyId);
    Optional<OntologyPropertyEntity> findByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    long deleteByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    long deleteByWorkspaceIdAndCompanyId(Long workspaceId, String companyId);
}
