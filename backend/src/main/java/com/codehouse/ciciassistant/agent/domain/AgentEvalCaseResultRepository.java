package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvalCaseResultRepository extends JpaRepository<AgentEvalCaseResultEntity, Long> {

    List<AgentEvalCaseResultEntity> findByCompanyIdAndRunIdOrderByIdAsc(String companyId, Long runId);
}
