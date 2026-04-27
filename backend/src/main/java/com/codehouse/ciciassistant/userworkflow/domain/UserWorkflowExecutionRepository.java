package com.codehouse.ciciassistant.userworkflow.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWorkflowExecutionRepository extends JpaRepository<UserWorkflowExecutionEntity, Long> {

    List<UserWorkflowExecutionEntity> findTop20ByOrgIdAndUserIdAndAgentIdOrderByIdDesc(String orgId, String userId, String agentId);

    Optional<UserWorkflowExecutionEntity> findByIdAndOrgIdAndUserIdAndAgentId(Long id, String orgId, String userId, String agentId);
}
