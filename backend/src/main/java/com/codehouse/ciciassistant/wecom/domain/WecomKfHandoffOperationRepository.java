package com.codehouse.ciciassistant.wecom.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WecomKfHandoffOperationRepository extends JpaRepository<WecomKfHandoffOperationEntity, Long> {

    Optional<WecomKfHandoffOperationEntity> findByCompanyIdAndConversationIdAndActorUserIdAndIdempotencyKey(
            String companyId,
            Long conversationId,
            String actorUserId,
            String idempotencyKey);

    Optional<WecomKfHandoffOperationEntity> findByOperationId(UUID operationId);
}
