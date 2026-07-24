package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentKnowledgeBindingRepository extends JpaRepository<AgentKnowledgeBindingEntity, Long> {

    List<AgentKnowledgeBindingEntity> findByCompanyIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(String companyId, String agentId);

    void deleteByCompanyIdAndAgentId(String companyId, String agentId);

    void deleteByCompanyIdAndKnowledgeBaseId(String companyId, Long knowledgeBaseId);

    long countByCompanyIdAndKnowledgeBaseId(String companyId, Long knowledgeBaseId);
}
