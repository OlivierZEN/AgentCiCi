package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentWorkflowExecutionLogRepository extends JpaRepository<AgentWorkflowExecutionLogEntity, Long> {

    List<AgentWorkflowExecutionLogEntity> findByCompanyIdAndAgentIdOrderByCreatedAtDesc(String companyId, String agentId, Pageable pageable);

    List<AgentWorkflowExecutionLogEntity> findByCompanyIdAndAgentIdAndVersionNoOrderByCreatedAtDesc(
            String companyId, String agentId, Integer versionNo, Pageable pageable);
}
