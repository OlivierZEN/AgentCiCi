package com.codehouse.ciciassistant.platform.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformAuditLogRepository extends JpaRepository<PlatformAuditLogEntity, Long> {

    List<PlatformAuditLogEntity> findTop100ByCompanyIdOrderByIdDesc(String companyId);

    @Query("""
            select item from PlatformAuditLogEntity item
            where item.companyId = :companyId
              and item.createdAt between :from and :to
              and (:eventType is null or lower(item.eventType) = :eventType)
              and (:resourceType is null or lower(item.resourceType) = :resourceType)
            order by item.createdAt desc
            """)
    List<PlatformAuditLogEntity> filterPlatformAuditLogs(
            @Param("companyId") String companyId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("eventType") String eventType,
            @Param("resourceType") String resourceType,
            Pageable pageable);

    @Query("""
            select item from PlatformAuditLogEntity item
            where item.companyId = :companyId
              and item.createdAt between :from and :to
              and (:eventType is null or lower(item.eventType) = :eventType)
              and (:resourceType is null or lower(item.resourceType) = :resourceType)
              and (:q is null
                or lower(item.userId) like concat('%', :q, '%')
                or lower(item.roleCode) like concat('%', :q, '%')
                or lower(item.eventType) like concat('%', :q, '%')
                or lower(item.resourceType) like concat('%', :q, '%')
                or lower(item.resourceKey) like concat('%', :q, '%')
                or lower(item.detail) like concat('%', :q, '%'))
            order by item.createdAt desc
            """)
    List<PlatformAuditLogEntity> searchPlatformAuditLogs(
            @Param("companyId") String companyId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("eventType") String eventType,
            @Param("resourceType") String resourceType,
            @Param("q") String q,
            Pageable pageable);
}
