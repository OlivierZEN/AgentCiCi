package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentPublishConfigRepository extends JpaRepository<AgentPublishConfigEntity, Long> {

    List<AgentPublishConfigEntity> findByCompanyIdAndAgentIdOrderByChannelIdAsc(String companyId, String agentId);

    Optional<AgentPublishConfigEntity> findByCompanyIdAndAgentIdAndChannelId(String companyId, String agentId, String channelId);

    void deleteByCompanyIdAndAgentId(String companyId, String agentId);
}
