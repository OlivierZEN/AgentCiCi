package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTaskReviewRepository extends JpaRepository<AgentTaskReviewEntity, Long> {
    List<AgentTaskReviewEntity> findByOrgIdAndRunIdOrderByReviewRoundAsc(String orgId, Long runId);
}
