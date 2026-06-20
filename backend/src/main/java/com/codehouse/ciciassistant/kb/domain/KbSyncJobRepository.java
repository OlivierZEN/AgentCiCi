package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbSyncJobRepository extends JpaRepository<KbSyncJobEntity, Long> {

    List<KbSyncJobEntity> findTop20ByOrgIdAndDataSourceIdOrderByIdDesc(String orgId, Long dataSourceId);
}
