package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvalCaseRepository extends JpaRepository<AgentEvalCaseEntity, Long> {

    List<AgentEvalCaseEntity> findByOrgIdAndSuiteIdAndStatusOrderByIdAsc(String orgId, Long suiteId, String status);

    long countByOrgIdAndSuiteIdAndStatus(String orgId, Long suiteId, String status);
}
