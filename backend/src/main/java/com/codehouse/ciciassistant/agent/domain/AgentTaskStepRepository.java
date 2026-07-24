package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTaskStepRepository extends JpaRepository<AgentTaskStepEntity, Long> {
    List<AgentTaskStepEntity> findByCompanyIdAndRunIdOrderByStepOrderAsc(String companyId, Long runId);
    Optional<AgentTaskStepEntity> findByIdAndCompanyIdAndRunId(Long id, String companyId, Long runId);
}
