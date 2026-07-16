package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface OntologyAiProposalRepository extends Repository<OntologyAiProposalEntity, Long> {
    List<OntologyAiProposalEntity> findByWorkspaceIdAndOrgIdOrderByCreatedAtDesc(Long workspaceId, String orgId);
    @Query("""
            SELECT proposal.workspaceId
            FROM OntologyAiProposalEntity proposal
            WHERE proposal.id = :id
              AND proposal.orgId = :orgId
            """)
    Optional<Long> findWorkspaceIdByIdAndOrgId(
            @Param("id") Long id,
            @Param("orgId") String orgId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT proposal
            FROM OntologyAiProposalEntity proposal
            WHERE proposal.id = :id
              AND proposal.orgId = :orgId
            """)
    Optional<OntologyAiProposalEntity> findForUpdateByIdAndOrgId(
            @Param("id") Long id,
            @Param("orgId") String orgId);

    Optional<OntologyAiProposalEntity> findByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByIdAndWorkspaceIdAndOrgId(Long id, Long workspaceId, String orgId);
    long deleteByWorkspaceIdAndOrgId(Long workspaceId, String orgId);
}
