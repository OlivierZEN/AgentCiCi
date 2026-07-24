package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyPhysicalObjectRepository extends Repository<OntologyPhysicalObjectEntity, Long> {
    List<OntologyPhysicalObjectEntity> findByWorkspaceIdAndCompanyIdOrderByIdAsc(Long workspaceId, String companyId);
    List<OntologyPhysicalObjectEntity> findByDataSourceIdAndWorkspaceIdAndCompanyIdOrderByIdAsc(
            Long dataSourceId, Long workspaceId, String companyId);
    Optional<OntologyPhysicalObjectEntity> findByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    long deleteByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    long deleteByDataSourceIdAndWorkspaceIdAndCompanyId(Long dataSourceId, Long workspaceId, String companyId);
    long deleteByWorkspaceIdAndCompanyId(Long workspaceId, String companyId);
}
