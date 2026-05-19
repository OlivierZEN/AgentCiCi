package com.codehouse.ciciassistant.openapi.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApiMessageRepository extends JpaRepository<AgentApiMessageEntity, String> {

    Optional<AgentApiMessageEntity> findFirstByCredentialIdAndIdempotencyKeyAndStatusOrderByCreatedAtDesc(
            Long credentialId,
            String idempotencyKey,
            String status);

    Optional<AgentApiMessageEntity> findByMessageIdAndOrgIdAndCredentialIdAndAgentId(
            String messageId,
            String orgId,
            Long credentialId,
            String agentId);

    List<AgentApiMessageEntity> findTop100ByOrgIdAndCredentialIdAndAgentIdAndExternalSessionIdOrderByCreatedAtDesc(
            String orgId,
            Long credentialId,
            String agentId,
            String externalSessionId);

    List<AgentApiMessageEntity> findTop100ByOrgIdAndCredentialIdAndAgentIdOrderByCreatedAtDesc(
            String orgId,
            Long credentialId,
            String agentId);
}
