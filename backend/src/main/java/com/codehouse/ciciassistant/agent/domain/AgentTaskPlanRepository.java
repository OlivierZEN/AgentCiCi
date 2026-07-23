package com.codehouse.ciciassistant.agent.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTaskPlanRepository extends JpaRepository<AgentTaskPlanEntity, Long> {
    Optional<AgentTaskPlanEntity> findTopByOrgIdAndRunIdOrderByRevisionNoDesc(String orgId, Long runId);
}
