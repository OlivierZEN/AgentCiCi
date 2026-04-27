package com.codehouse.ciciassistant.skill.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSkillBindingRepository extends JpaRepository<AgentSkillBindingEntity, Long> {

    boolean existsByOrgIdAndAgentIdAndSkillId(String orgId, String agentId, Long skillId);

    List<AgentSkillBindingEntity> findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(String orgId, String agentId);

    void deleteByOrgIdAndAgentId(String orgId, String agentId);
}
