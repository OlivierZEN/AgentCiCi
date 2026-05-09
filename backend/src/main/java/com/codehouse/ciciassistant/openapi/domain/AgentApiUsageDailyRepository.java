package com.codehouse.ciciassistant.openapi.domain;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApiUsageDailyRepository extends JpaRepository<AgentApiUsageDailyEntity, Long> {

    Optional<AgentApiUsageDailyEntity> findByOrgIdAndCredentialIdAndUsageDate(
            String orgId,
            Long credentialId,
            LocalDate usageDate);
}
