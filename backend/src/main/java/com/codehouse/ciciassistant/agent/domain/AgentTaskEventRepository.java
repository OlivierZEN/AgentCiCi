package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTaskEventRepository extends JpaRepository<AgentTaskEventEntity, Long> {
    List<AgentTaskEventEntity> findByCompanyIdAndRunIdOrderByOccurredAtAscIdAsc(String companyId, Long runId);
}
