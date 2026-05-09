package com.codehouse.ciciassistant.openapi.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApiSessionMapRepository extends JpaRepository<AgentApiSessionMapEntity, Long> {

    Optional<AgentApiSessionMapEntity> findByOrgIdAndCredentialIdAndAgentIdAndExternalSessionId(
            String orgId,
            Long credentialId,
            String agentId,
            String externalSessionId);

    Optional<AgentApiSessionMapEntity> findByInternalSessionId(String internalSessionId);
}
