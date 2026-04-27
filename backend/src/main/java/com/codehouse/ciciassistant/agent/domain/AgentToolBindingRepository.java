package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentToolBindingRepository extends JpaRepository<AgentToolBindingEntity, Long> {

    List<AgentToolBindingEntity> findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(String orgId, String agentId);

    void deleteByOrgIdAndAgentId(String orgId, String agentId);
}
