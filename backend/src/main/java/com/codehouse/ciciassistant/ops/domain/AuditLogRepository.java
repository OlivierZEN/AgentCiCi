package com.codehouse.ciciassistant.ops.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    List<AuditLogEntity> findTop50ByOrgIdOrderByIdDesc(String orgId);

    List<AuditLogEntity> findByOrgIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            String orgId,
            Instant from,
            Instant to,
            Pageable pageable);
}
