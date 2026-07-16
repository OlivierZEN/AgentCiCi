package com.codehouse.ciciassistant.ontology.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyAiProposalRepository extends JpaRepository<OntologyAiProposalEntity, Long> {
    List<OntologyAiProposalEntity> findByWorkspaceIdAndOrgIdOrderByCreatedAtDesc(Long workspaceId, String orgId);
    Optional<OntologyAiProposalEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
}
