package com.codehouse.ciciassistant.openapi.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApiSessionMapRepository extends JpaRepository<AgentApiSessionMapEntity, Long> {

    Optional<AgentApiSessionMapEntity> findByOrgIdAndCredentialIdAndAgentIdAndExternalSessionId(
            String orgId,
            Long credentialId,
            String agentId,
            String externalSessionId);

    Optional<AgentApiSessionMapEntity> findByInternalSessionId(String internalSessionId);

    List<AgentApiSessionMapEntity> findTop100ByOrgIdAndCredentialIdAndAgentIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            String orgId,
            Long credentialId,
            String agentId);

    Optional<AgentApiSessionMapEntity> findByOrgIdAndCredentialIdAndAgentIdAndExternalSessionIdAndDeletedAtIsNull(
            String orgId,
            Long credentialId,
            String agentId,
            String externalSessionId);
}
