package com.codehouse.ciciassistant.openapi.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApiCallLogRepository extends JpaRepository<AgentApiCallLogEntity, String> {

    Optional<AgentApiCallLogEntity> findByRequestIdAndCredentialId(String requestId, Long credentialId);

    List<AgentApiCallLogEntity> findTop100ByCompanyIdAndAgentIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            String companyId,
            String agentId,
            Instant from,
            Instant to);
}
