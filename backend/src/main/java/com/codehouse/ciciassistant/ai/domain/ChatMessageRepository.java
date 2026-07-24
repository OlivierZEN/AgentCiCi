package com.codehouse.ciciassistant.ai.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    Optional<ChatMessageEntity> findFirstByCompanyIdAndSessionIdOrderByCreatedAtDesc(String companyId, String sessionId);

    List<ChatMessageEntity> findByCompanyIdAndSessionIdOrderByCreatedAtAsc(String companyId, String sessionId);

    List<ChatMessageEntity> findByCompanyIdAndSessionIdOrderByCreatedAtDesc(String companyId, String sessionId, Pageable pageable);

    void deleteByCompanyIdAndSessionId(String companyId, String sessionId);
}
