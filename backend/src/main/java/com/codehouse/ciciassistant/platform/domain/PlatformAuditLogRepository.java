package com.codehouse.ciciassistant.platform.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAuditLogRepository extends JpaRepository<PlatformAuditLogEntity, Long> {

    List<PlatformAuditLogEntity> findTop100ByOrgIdOrderByIdDesc(String orgId);
}
