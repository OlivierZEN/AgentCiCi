package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvalPublishReferenceRepository extends JpaRepository<AgentEvalPublishReferenceEntity, Long> {

    List<AgentEvalPublishReferenceEntity> findByCompanyIdAndAgentIdAndVersionNoOrderByPublishedAtDesc(
            String companyId, String agentId, Integer versionNo);
}
