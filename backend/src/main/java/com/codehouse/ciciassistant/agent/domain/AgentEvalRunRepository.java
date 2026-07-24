package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvalRunRepository extends JpaRepository<AgentEvalRunEntity, Long> {

    List<AgentEvalRunEntity> findByCompanyIdAndSuiteIdOrderByCreatedAtDesc(String companyId, Long suiteId);

    List<AgentEvalRunEntity> findByCompanyIdAndAgentIdAndVersionNoOrderByCreatedAtDesc(
            String companyId,
            String agentId,
            Integer versionNo);

    List<AgentEvalRunEntity> findByCompanyIdOrderByCreatedAtDesc(String companyId);

    List<AgentEvalRunEntity> findTop200ByOrderByCreatedAtDesc();
}
