package com.codehouse.ciciassistant.userworkflow.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWorkflowExecutionRepository extends JpaRepository<UserWorkflowExecutionEntity, Long> {

    List<UserWorkflowExecutionEntity> findTop20ByCompanyIdAndUserIdAndAgentIdOrderByIdDesc(String companyId, String userId, String agentId);

    Optional<UserWorkflowExecutionEntity> findByIdAndCompanyIdAndUserIdAndAgentId(Long id, String companyId, String userId, String agentId);
}
