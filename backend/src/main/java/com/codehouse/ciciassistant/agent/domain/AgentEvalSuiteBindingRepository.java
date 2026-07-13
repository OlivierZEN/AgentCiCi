package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvalSuiteBindingRepository extends JpaRepository<AgentEvalSuiteBindingEntity, Long> {

    List<AgentEvalSuiteBindingEntity> findByEnabledTrueOrderByIdAsc();

    List<AgentEvalSuiteBindingEntity> findBySuiteIdOrderByIdAsc(Long suiteId);
}
