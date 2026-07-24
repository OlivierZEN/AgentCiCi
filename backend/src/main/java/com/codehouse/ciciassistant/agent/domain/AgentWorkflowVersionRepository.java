package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentWorkflowVersionRepository extends JpaRepository<AgentWorkflowVersionEntity, Long> {

    Optional<AgentWorkflowVersionEntity> findTopByCompanyIdAndAgentIdOrderByVersionNoDesc(String companyId, String agentId);

    List<AgentWorkflowVersionEntity> findByCompanyIdAndAgentIdOrderByVersionNoDesc(String companyId, String agentId);

    List<AgentWorkflowVersionEntity> findByCompanyIdAndIdIn(String companyId, List<Long> ids);

    Optional<AgentWorkflowVersionEntity> findByCompanyIdAndAgentIdAndVersionNo(String companyId, String agentId, Integer versionNo);

    Optional<AgentWorkflowVersionEntity> findByCompanyIdAndAgentIdAndPublishStatus(String companyId, String agentId, String publishStatus);
}
