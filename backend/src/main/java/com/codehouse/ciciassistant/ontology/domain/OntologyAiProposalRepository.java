package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface OntologyAiProposalRepository extends Repository<OntologyAiProposalEntity, Long> {
    List<OntologyAiProposalEntity> findByWorkspaceIdAndCompanyIdOrderByCreatedAtDesc(Long workspaceId, String companyId);
    @Query("""
            SELECT proposal.workspaceId
            FROM OntologyAiProposalEntity proposal
            WHERE proposal.id = :id
              AND proposal.companyId = :companyId
            """)
    Optional<Long> findWorkspaceIdByIdAndCompanyId(
            @Param("id") Long id,
            @Param("companyId") String companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT proposal
            FROM OntologyAiProposalEntity proposal
            WHERE proposal.id = :id
              AND proposal.companyId = :companyId
            """)
    Optional<OntologyAiProposalEntity> findForUpdateByIdAndCompanyId(
            @Param("id") Long id,
            @Param("companyId") String companyId);

    Optional<OntologyAiProposalEntity> findByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    long deleteByIdAndWorkspaceIdAndCompanyId(Long id, Long workspaceId, String companyId);
    long deleteByWorkspaceIdAndCompanyId(Long workspaceId, String companyId);
}
