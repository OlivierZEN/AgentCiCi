package com.codehouse.ciciassistant.openapi.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApiTaskRepository extends JpaRepository<AgentApiTaskEntity, String> {

    Optional<AgentApiTaskEntity> findByTaskIdAndOrgIdAndCredentialIdAndAgentId(
            String taskId,
            String orgId,
            Long credentialId,
            String agentId);
}
