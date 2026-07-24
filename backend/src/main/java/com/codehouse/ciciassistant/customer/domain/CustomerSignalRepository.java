package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CustomerSignalRepository extends JpaRepository<CustomerSignalEntity, Long> {
    List<CustomerSignalEntity> findByCompanyIdAndCrmAccountIdOrderByUpdatedAtDesc(String companyId, String crmAccountId);
    Optional<CustomerSignalEntity> findByCompanyIdAndPublicId(String companyId, String publicId);
    long countByCompanyIdAndCrmAccountIdAndStatus(String companyId, String crmAccountId, String status);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO customer_signal
                (public_id, company_id, crm_account_id, mode, signal_type, title, detail, severity, status,
                 evidence_json, assignee, source_updated_at, created_at, updated_at)
            VALUES
                (:publicId, :companyId, :accountId, :mode, :signalType, :title, :detail, :severity, 'OPEN',
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
                     @Param("companyId") String companyId,
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
