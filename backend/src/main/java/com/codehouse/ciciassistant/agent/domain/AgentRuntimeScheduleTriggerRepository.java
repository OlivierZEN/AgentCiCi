package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRuntimeScheduleTriggerRepository extends JpaRepository<AgentRuntimeScheduleTriggerEntity, Long> {

    List<AgentRuntimeScheduleTriggerEntity> findByOrgIdAndAgentIdAndActiveTrueOrderByIdAsc(String orgId, String agentId);

    java.util.Optional<AgentRuntimeScheduleTriggerEntity> findByOrgIdAndAgentIdAndTriggerKeyAndActiveTrue(
            String orgId,
            String agentId,
            String triggerKey);
}
