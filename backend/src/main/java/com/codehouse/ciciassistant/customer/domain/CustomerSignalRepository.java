package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerSignalRepository extends JpaRepository<CustomerSignalEntity, Long> {
    List<CustomerSignalEntity> findByOrgIdAndCrmAccountIdOrderByUpdatedAtDesc(String orgId, String crmAccountId);
    Optional<CustomerSignalEntity> findByOrgIdAndPublicId(String orgId, String publicId);
    long countByOrgIdAndCrmAccountIdAndStatus(String orgId, String crmAccountId, String status);

    @Modifying
    @Query(value = """
            INSERT INTO customer_signal
                (public_id, org_id, crm_account_id, mode, signal_type, title, detail, severity, status,
                 evidence_json, assignee, source_updated_at, created_at, updated_at)
            VALUES
                (:publicId, :orgId, :accountId, :mode, :signalType, :title, :detail, :severity, 'OPEN',
                 :evidenceJson, NULL, :sourceUpdatedAt, :now, :now)
            ON CONFLICT (public_id) DO UPDATE SET
                mode = EXCLUDED.mode,
                title = EXCLUDED.title,
                detail = EXCLUDED.detail,
                severity = EXCLUDED.severity,
                status = 'OPEN',
                evidence_json = EXCLUDED.evidence_json,
                source_updated_at = EXCLUDED.source_updated_at,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsertSignal(@Param("publicId") String publicId,
                     @Param("orgId") String orgId,
                     @Param("accountId") String accountId,
                     @Param("mode") String mode,
                     @Param("signalType") String signalType,
                     @Param("title") String title,
                     @Param("detail") String detail,
                     @Param("severity") String severity,
                     @Param("evidenceJson") String evidenceJson,
                     @Param("sourceUpdatedAt") Instant sourceUpdatedAt,
                     @Param("now") Instant now);
}
