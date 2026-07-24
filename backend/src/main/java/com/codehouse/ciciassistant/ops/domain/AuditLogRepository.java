package com.codehouse.ciciassistant.ops.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    List<AuditLogEntity> findTop50ByCompanyIdOrderByIdDesc(String companyId);

    List<AuditLogEntity> findByCompanyIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            String companyId,
            Instant from,
            Instant to,
            Pageable pageable);

    @Query("""
            select item from AuditLogEntity item
            where item.companyId = :companyId
              and item.createdAt between :from and :to
              and (:eventType is null or lower(item.eventType) = :eventType)
              and (:q is null
                or lower(item.userId) like concat('%', :q, '%')
                or lower(item.eventType) like concat('%', :q, '%')
                or lower(item.detail) like concat('%', :q, '%'))
            order by item.createdAt desc
            """)
    List<AuditLogEntity> searchOrgAuditLogs(
            @Param("companyId") String companyId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("eventType") String eventType,
            @Param("q") String q,
            Pageable pageable);
}
