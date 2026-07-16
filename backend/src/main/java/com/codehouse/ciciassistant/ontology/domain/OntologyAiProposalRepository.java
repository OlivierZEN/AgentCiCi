package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface OntologyAiProposalRepository extends Repository<OntologyAiProposalEntity, Long> {
    OntologyAiProposalEntity save(OntologyAiProposalEntity entity);
    List<OntologyAiProposalEntity> findByWorkspaceIdAndOrgIdOrderByCreatedAtDesc(Long workspaceId, String orgId);
    Optional<OntologyAiProposalEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByWorkspaceIdAndOrgId(Long workspaceId, String orgId);
}
