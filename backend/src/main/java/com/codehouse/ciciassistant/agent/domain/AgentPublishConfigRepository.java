package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentPublishConfigRepository extends JpaRepository<AgentPublishConfigEntity, Long> {

    List<AgentPublishConfigEntity> findByOrgIdAndAgentIdOrderByChannelIdAsc(String orgId, String agentId);

    Optional<AgentPublishConfigEntity> findByOrgIdAndAgentIdAndChannelId(String orgId, String agentId, String channelId);

    void deleteByOrgIdAndAgentId(String orgId, String agentId);
}
