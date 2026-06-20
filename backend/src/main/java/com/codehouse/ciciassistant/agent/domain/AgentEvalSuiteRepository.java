package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvalSuiteRepository extends JpaRepository<AgentEvalSuiteEntity, Long> {

    List<AgentEvalSuiteEntity> findByOrgIdAndAgentIdAndStatusOrderByIdAsc(String orgId, String agentId, String status);

    Optional<AgentEvalSuiteEntity> findByIdAndOrgId(Long id, String orgId);
}
