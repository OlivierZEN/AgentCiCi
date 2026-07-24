package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvalIssueRepository extends JpaRepository<AgentEvalIssueEntity, Long> {

    List<AgentEvalIssueEntity> findByCompanyIdOrderByUpdatedAtDesc(String companyId);

    long countByCompanyIdAndStatus(String companyId, String status);
}
