package com.codehouse.ciciassistant.ai.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    Optional<ChatMessageEntity> findFirstByOrgIdAndSessionIdOrderByCreatedAtDesc(String orgId, String sessionId);

    List<ChatMessageEntity> findByOrgIdAndSessionIdOrderByCreatedAtAsc(String orgId, String sessionId);

    List<ChatMessageEntity> findByOrgIdAndSessionIdOrderByCreatedAtDesc(String orgId, String sessionId, Pageable pageable);

    void deleteByOrgIdAndSessionId(String orgId, String sessionId);
}
