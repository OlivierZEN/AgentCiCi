package com.codehouse.ciciassistant.skill.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSkillBindingRepository extends JpaRepository<AgentSkillBindingEntity, Long> {

    boolean existsByCompanyIdAndAgentIdAndSkillId(String companyId, String agentId, Long skillId);

    boolean existsByCompanyIdAndAgentIdAndSkillIdAndEnabledTrue(String companyId, String agentId, Long skillId);

    List<AgentSkillBindingEntity> findByCompanyIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(String companyId, String agentId);

    List<AgentSkillBindingEntity> findTop1001ByCompanyIdAndSkillIdAndEnabledTrueOrderByAgentIdAscPriorityAsc(
            String companyId, Long skillId);

    List<AgentSkillBindingEntity> findByCompanyIdAndSkillIdInAndEnabledTrue(String companyId, List<Long> skillIds);

    void deleteByCompanyIdAndAgentId(String companyId, String agentId);
}
