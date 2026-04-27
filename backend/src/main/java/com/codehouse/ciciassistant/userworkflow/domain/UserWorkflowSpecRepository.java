package com.codehouse.ciciassistant.userworkflow.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWorkflowSpecRepository extends JpaRepository<UserWorkflowSpecEntity, Long> {

    Optional<UserWorkflowSpecEntity> findByOrgIdAndUserIdAndAgentId(String orgId, String userId, String agentId);
}
