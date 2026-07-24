package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyPhysicalFieldRepository extends Repository<OntologyPhysicalFieldEntity, Long> {
    List<OntologyPhysicalFieldEntity> findByWorkspaceIdAndCompanyIdOrderByIdAsc(Long workspaceId, String companyId);
    List<OntologyPhysicalFieldEntity> findByPhysicalObjectIdAndWorkspaceIdAndCompanyIdOrderByIdAsc(
            Long physicalObjectId, Long workspaceId, String companyId);
    Optional<OntologyPhysicalFieldEntity> findByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    long deleteByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    long deleteByWorkspaceIdAndCompanyId(Long workspaceId, String companyId);
}
