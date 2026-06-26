package com.codehouse.ciciassistant.ai.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ChatSessionStateRepository extends JpaRepository<ChatSessionStateEntity, ChatSessionStateId> {

    Optional<ChatSessionStateEntity> findBySessionIdAndOrgId(String sessionId, String orgId);

    @Transactional
    void deleteBySessionIdAndOrgId(String sessionId, String orgId);
}
