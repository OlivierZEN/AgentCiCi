package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvalCaseRepository extends JpaRepository<AgentEvalCaseEntity, Long> {

    List<AgentEvalCaseEntity> findByCompanyIdAndSuiteIdAndStatusOrderByIdAsc(String companyId, Long suiteId, String status);

    long countByCompanyIdAndSuiteIdAndStatus(String companyId, Long suiteId, String status);

    long countBySuiteIdAndStatus(Long suiteId, String status);

    List<AgentEvalCaseEntity> findBySuiteIdAndStatusOrderByIdAsc(Long suiteId, String status);

    List<AgentEvalCaseEntity> findBySuiteIdOrderByIdAsc(Long suiteId);
}
