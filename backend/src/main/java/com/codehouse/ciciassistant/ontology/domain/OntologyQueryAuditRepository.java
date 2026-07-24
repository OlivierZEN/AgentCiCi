package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyQueryAuditRepository extends Repository<OntologyQueryAuditEntity, Long> {
    List<OntologyQueryAuditEntity> findByWorkspaceIdAndCompanyIdOrderByCreatedAtDesc(Long workspaceId, String companyId);
    List<OntologyQueryAuditEntity> findByCompanyIdOrderByCreatedAtDesc(String companyId);
    Optional<OntologyQueryAuditEntity> findByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    long deleteByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    long deleteByWorkspaceIdAndCompanyId(Long workspaceId, String companyId);
}
