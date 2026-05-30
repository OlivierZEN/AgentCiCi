package com.codehouse.ciciassistant.billing.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageMeterEventRepository extends JpaRepository<UsageMeterEventEntity, Long> {

    boolean existsByOrgId(String orgId);

    Optional<UsageMeterEventEntity> findBySourceTypeAndSourceId(String sourceType, String sourceId);

    List<UsageMeterEventEntity> findTop100ByOrgIdOrderByOccurredAtDesc(String orgId);

    List<UsageMeterEventEntity> findTop20ByOrgIdOrderByOccurredAtDesc(String orgId);
}
