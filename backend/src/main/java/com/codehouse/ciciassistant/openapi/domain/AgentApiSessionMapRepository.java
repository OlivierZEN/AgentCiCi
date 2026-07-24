package com.codehouse.ciciassistant.openapi.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApiSessionMapRepository extends JpaRepository<AgentApiSessionMapEntity, Long> {

    Optional<AgentApiSessionMapEntity> findByCompanyIdAndCredentialIdAndAgentIdAndExternalSessionId(
            String companyId,
            Long credentialId,
            String agentId,
            String externalSessionId);

    Optional<AgentApiSessionMapEntity> findByInternalSessionId(String internalSessionId);

    List<AgentApiSessionMapEntity> findTop100ByCompanyIdAndCredentialIdAndAgentIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            String companyId,
            Long credentialId,
            String agentId);

    Optional<AgentApiSessionMapEntity> findByCompanyIdAndCredentialIdAndAgentIdAndExternalSessionIdAndDeletedAtIsNull(
            String companyId,
            Long credentialId,
            String agentId,
            String externalSessionId);
}
