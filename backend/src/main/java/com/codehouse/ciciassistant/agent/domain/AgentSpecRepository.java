package com.codehouse.ciciassistant.agent.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSpecRepository extends JpaRepository<AgentSpecEntity, Long> {

    Optional<AgentSpecEntity> findByCompanyIdAndAgentId(String companyId, String agentId);
}
