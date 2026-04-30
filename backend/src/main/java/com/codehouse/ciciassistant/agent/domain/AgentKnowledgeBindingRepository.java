package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentKnowledgeBindingRepository extends JpaRepository<AgentKnowledgeBindingEntity, Long> {

    List<AgentKnowledgeBindingEntity> findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(String orgId, String agentId);

    void deleteByOrgIdAndAgentId(String orgId, String agentId);

    void deleteByOrgIdAndKnowledgeBaseId(String orgId, Long knowledgeBaseId);

    long countByOrgIdAndKnowledgeBaseId(String orgId, Long knowledgeBaseId);
}
