package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentWorkflowVersionRepository extends JpaRepository<AgentWorkflowVersionEntity, Long> {

    Optional<AgentWorkflowVersionEntity> findTopByOrgIdAndAgentIdOrderByVersionNoDesc(String orgId, String agentId);

    List<AgentWorkflowVersionEntity> findByOrgIdAndAgentIdOrderByVersionNoDesc(String orgId, String agentId);

    List<AgentWorkflowVersionEntity> findByOrgIdAndIdIn(String orgId, List<Long> ids);

    Optional<AgentWorkflowVersionEntity> findByOrgIdAndAgentIdAndVersionNo(String orgId, String agentId, Integer versionNo);

    Optional<AgentWorkflowVersionEntity> findByOrgIdAndAgentIdAndPublishStatus(String orgId, String agentId, String publishStatus);
}
