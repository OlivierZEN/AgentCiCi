package com.codehouse.ciciassistant.ai.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionStateRepository extends JpaRepository<ChatSessionStateEntity, String> {

    Optional<ChatSessionStateEntity> findBySessionIdAndOrgId(String sessionId, String orgId);

    void deleteBySessionIdAndOrgId(String sessionId, String orgId);
}
