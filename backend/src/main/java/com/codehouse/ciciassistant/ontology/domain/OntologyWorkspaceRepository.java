package com.codehouse.ciciassistant.ontology.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface OntologyWorkspaceRepository extends Repository<OntologyWorkspaceEntity, Long> {

    List<OntologyWorkspaceEntity> findByOrgIdOrderByUpdatedAtDesc(String orgId);

    Optional<OntologyWorkspaceEntity> findByIdAndOrgId(Long id, String orgId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT workspace
            FROM OntologyWorkspaceEntity workspace
            WHERE workspace.id = :id
              AND workspace.orgId = :orgId
            """)
    Optional<OntologyWorkspaceEntity> findForUpdateByIdAndOrgId(
            @Param("id") Long id,
            @Param("orgId") String orgId);

    Optional<OntologyWorkspaceEntity> findByOrgIdAndKey(String orgId, String key);

    long deleteByIdAndOrgId(Long id, String orgId);
}
