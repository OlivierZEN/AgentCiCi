package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentChannelBindingRepository extends JpaRepository<AgentChannelBindingEntity, Long> {

    List<AgentChannelBindingEntity> findByCompanyIdAndAgentIdAndEnabledTrueOrderByIdAsc(String companyId, String agentId);

    List<AgentChannelBindingEntity> findByCompanyIdAndAgentIdInAndEnabledTrueOrderByIdAsc(String companyId, List<String> agentIds);

    boolean existsByCompanyIdAndAgentIdAndChannelIdAndEnabledTrue(String companyId, String agentId, String channelId);

    void deleteByCompanyIdAndAgentId(String companyId, String agentId);
}
