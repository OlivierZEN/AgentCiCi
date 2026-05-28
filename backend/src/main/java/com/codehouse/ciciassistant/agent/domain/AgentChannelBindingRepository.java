package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentChannelBindingRepository extends JpaRepository<AgentChannelBindingEntity, Long> {

    List<AgentChannelBindingEntity> findByOrgIdAndAgentIdAndEnabledTrueOrderByIdAsc(String orgId, String agentId);

    List<AgentChannelBindingEntity> findByOrgIdAndAgentIdInAndEnabledTrueOrderByIdAsc(String orgId, List<String> agentIds);

    boolean existsByOrgIdAndAgentIdAndChannelIdAndEnabledTrue(String orgId, String agentId, String channelId);

    void deleteByOrgIdAndAgentId(String orgId, String agentId);
}
