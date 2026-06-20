package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvalRunRepository extends JpaRepository<AgentEvalRunEntity, Long> {

    List<AgentEvalRunEntity> findByOrgIdAndSuiteIdOrderByCreatedAtDesc(String orgId, Long suiteId);

    List<AgentEvalRunEntity> findByOrgIdAndAgentIdAndVersionNoOrderByCreatedAtDesc(
            String orgId,
            String agentId,
            Integer versionNo);
}
