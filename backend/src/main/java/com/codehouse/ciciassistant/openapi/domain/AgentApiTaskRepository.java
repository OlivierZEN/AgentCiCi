package com.codehouse.ciciassistant.openapi.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApiTaskRepository extends JpaRepository<AgentApiTaskEntity, String> {

    Optional<AgentApiTaskEntity> findByTaskIdAndCompanyIdAndCredentialIdAndAgentId(
            String taskId,
            String companyId,
            Long credentialId,
            String agentId);
}
