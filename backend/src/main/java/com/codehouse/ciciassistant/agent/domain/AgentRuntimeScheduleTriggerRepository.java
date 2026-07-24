package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRuntimeScheduleTriggerRepository extends JpaRepository<AgentRuntimeScheduleTriggerEntity, Long> {

    List<AgentRuntimeScheduleTriggerEntity> findByCompanyIdAndAgentIdAndActiveTrueOrderByIdAsc(String companyId, String agentId);

    java.util.Optional<AgentRuntimeScheduleTriggerEntity> findByCompanyIdAndAgentIdAndTriggerKeyAndActiveTrue(
            String companyId,
            String agentId,
            String triggerKey);

    long deleteByCompanyIdAndAgentIdAndSource(String companyId, String agentId, String source);
}
