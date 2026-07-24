package com.codehouse.ciciassistant.openapi.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApiMessageRepository extends JpaRepository<AgentApiMessageEntity, String> {

    Optional<AgentApiMessageEntity> findFirstByCredentialIdAndIdempotencyKeyAndStatusOrderByCreatedAtDesc(
            Long credentialId,
            String idempotencyKey,
            String status);

    Optional<AgentApiMessageEntity> findByMessageIdAndCompanyIdAndCredentialIdAndAgentId(
            String messageId,
            String companyId,
            Long credentialId,
            String agentId);

    List<AgentApiMessageEntity> findTop100ByCompanyIdAndCredentialIdAndAgentIdAndExternalSessionIdOrderByCreatedAtDesc(
            String companyId,
            Long credentialId,
            String agentId,
            String externalSessionId);

    List<AgentApiMessageEntity> findTop100ByCompanyIdAndCredentialIdAndAgentIdOrderByCreatedAtDesc(
            String companyId,
            Long credentialId,
            String agentId);
}
