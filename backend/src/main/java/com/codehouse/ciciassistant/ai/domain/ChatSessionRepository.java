package com.codehouse.ciciassistant.ai.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {

    List<ChatSessionEntity> findByOrgIdOrderByUpdatedAtDesc(String orgId);

    List<ChatSessionEntity> findByOrgIdAndUserIdOrderByUpdatedAtDesc(String orgId, String userId);

    Optional<ChatSessionEntity> findByIdAndOrgId(String id, String orgId);

    Optional<ChatSessionEntity> findByIdAndOrgIdAndUserId(String id, String orgId, String userId);
}
