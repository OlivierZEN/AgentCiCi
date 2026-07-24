package com.codehouse.ciciassistant.openapi.domain;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApiUsageDailyRepository extends JpaRepository<AgentApiUsageDailyEntity, Long> {

    Optional<AgentApiUsageDailyEntity> findByCompanyIdAndCredentialIdAndUsageDate(
            String companyId,
            Long credentialId,
            LocalDate usageDate);
}
