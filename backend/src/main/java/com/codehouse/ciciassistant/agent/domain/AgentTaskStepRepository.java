package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTaskStepRepository extends JpaRepository<AgentTaskStepEntity, Long> {
    List<AgentTaskStepEntity> findByOrgIdAndRunIdOrderByStepOrderAsc(String orgId, Long runId);
    Optional<AgentTaskStepEntity> findByIdAndOrgIdAndRunId(Long id, String orgId, Long runId);
}
