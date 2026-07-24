package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface OntologyWorkspaceRepository extends Repository<OntologyWorkspaceEntity, Long> {

    List<OntologyWorkspaceEntity> findByCompanyIdOrderByUpdatedAtDesc(String companyId);

    Optional<OntologyWorkspaceEntity> findByIdAndCompanyId(Long id, String companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT workspace
            FROM OntologyWorkspaceEntity workspace
            WHERE workspace.id = :id
              AND workspace.companyId = :companyId
            """)
    Optional<OntologyWorkspaceEntity> findForUpdateByIdAndCompanyId(
            @Param("id") Long id,
            @Param("companyId") String companyId);

    Optional<OntologyWorkspaceEntity> findByCompanyIdAndKey(String companyId, String key);

    long deleteByIdAndCompanyId(Long id, String companyId);
}
