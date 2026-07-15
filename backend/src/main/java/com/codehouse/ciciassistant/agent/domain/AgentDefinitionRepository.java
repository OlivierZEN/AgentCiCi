package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentDefinitionRepository extends JpaRepository<AgentDefinitionEntity, Long> {

    List<AgentDefinitionEntity> findByOrgIdAndEnabledTrueOrderByBuiltinDescUpdatedAtDesc(String orgId);

    List<AgentDefinitionEntity> findTop24ByOrgIdAndEnabledTrueOrderByBuiltinDescUpdatedAtDesc(String orgId);

    Optional<AgentDefinitionEntity> findByOrgIdAndAgentId(String orgId, String agentId);

    List<AgentDefinitionEntity> findByOrgIdAndAgentIdIn(String orgId, List<String> agentIds);

    Optional<AgentDefinitionEntity> findByOrgIdAndAgentIdAndEnabledTrue(String orgId, String agentId);

    boolean existsByOrgIdAndAgentId(String orgId, String agentId);
}
