package com.codehouse.ciciassistant.embed.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SisiEmbedSessionRepository extends JpaRepository<SisiEmbedSessionEntity, String> {

    Optional<SisiEmbedSessionEntity> findByCompanyIdAndAgentIdAndExternalTenantIdAndExternalUserIdAndSourceAndObjectTypeAndObjectId(
            String companyId,
            String agentId,
            String externalTenantId,
            String externalUserId,
            String source,
            String objectType,
            String objectId);

    Optional<SisiEmbedSessionEntity> findByChatSessionIdAndCompanyId(String chatSessionId, String companyId);

    Optional<SisiEmbedSessionEntity> findFirstByCompanyIdAndAgentIdAndExternalTenantIdAndExternalUserIdAndSourceOrderByUpdatedAtDesc(
            String companyId,
            String agentId,
            String externalTenantId,
            String externalUserId,
            String source);

    List<SisiEmbedSessionEntity> findByCompanyIdOrderByUpdatedAtDesc(String companyId, Pageable pageable);
}
