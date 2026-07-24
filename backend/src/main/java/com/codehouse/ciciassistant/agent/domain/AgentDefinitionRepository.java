package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentDefinitionRepository extends JpaRepository<AgentDefinitionEntity, Long> {

    List<AgentDefinitionEntity> findByCompanyIdAndEnabledTrueOrderByBuiltinDescUpdatedAtDesc(String companyId);

    List<AgentDefinitionEntity> findTop24ByCompanyIdAndEnabledTrueOrderByBuiltinDescUpdatedAtDesc(String companyId);

    Optional<AgentDefinitionEntity> findByCompanyIdAndAgentId(String companyId, String agentId);

    List<AgentDefinitionEntity> findByCompanyIdAndAgentIdIn(String companyId, List<String> agentIds);

    Optional<AgentDefinitionEntity> findByCompanyIdAndAgentIdAndEnabledTrue(String companyId, String agentId);

    boolean existsByCompanyIdAndAgentId(String companyId, String agentId);
}
