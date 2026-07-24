package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyDataSourceRepository extends Repository<OntologyDataSourceEntity, Long> {
    List<OntologyDataSourceEntity> findByWorkspaceIdAndCompanyIdOrderByIdAsc(Long workspaceId, String companyId);
    Optional<OntologyDataSourceEntity> findByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    Optional<OntologyDataSourceEntity> findByWorkspaceIdAndCompanyIdAndKey(Long workspaceId, String companyId, String key);
    long deleteByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    long deleteByWorkspaceIdAndCompanyId(Long workspaceId, String companyId);
}
