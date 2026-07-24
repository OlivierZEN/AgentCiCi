package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentToolBindingRepository extends JpaRepository<AgentToolBindingEntity, Long> {

    List<AgentToolBindingEntity> findByCompanyIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(String companyId, String agentId);

    void deleteByCompanyIdAndAgentId(String companyId, String agentId);
}
