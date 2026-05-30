package com.codehouse.ciciassistant.billing.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingConfigChangeLogRepository extends JpaRepository<BillingConfigChangeLogEntity, Long> {

    List<BillingConfigChangeLogEntity> findTop50ByConfigTypeAndConfigCodeOrderByVersionNoDescIdDesc(String configType,
                                                                                                     String configCode);
}
