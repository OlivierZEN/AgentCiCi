package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentWorkflowExecutionLogRepository extends JpaRepository<AgentWorkflowExecutionLogEntity, Long> {

    List<AgentWorkflowExecutionLogEntity> findByOrgIdAndAgentIdOrderByCreatedAtDesc(String orgId, String agentId, Pageable pageable);

    List<AgentWorkflowExecutionLogEntity> findByOrgIdAndAgentIdAndVersionNoOrderByCreatedAtDesc(
            String orgId, String agentId, Integer versionNo, Pageable pageable);
}
