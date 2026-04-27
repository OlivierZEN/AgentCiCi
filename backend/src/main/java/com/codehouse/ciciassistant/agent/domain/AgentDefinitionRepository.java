package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentDefinitionRepository extends JpaRepository<AgentDefinitionEntity, Long> {

    List<AgentDefinitionEntity> findByOrgIdOrderByBuiltinDescUpdatedAtDesc(String orgId);

    Optional<AgentDefinitionEntity> findByOrgIdAndAgentId(String orgId, String agentId);

    boolean existsByOrgIdAndAgentId(String orgId, String agentId);
}
