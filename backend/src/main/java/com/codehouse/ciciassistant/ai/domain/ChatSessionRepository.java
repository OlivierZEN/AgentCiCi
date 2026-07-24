package com.codehouse.ciciassistant.ai.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {

    List<ChatSessionEntity> findByCompanyIdOrderByUpdatedAtDesc(String companyId);

    List<ChatSessionEntity> findByCompanyIdAndUserIdOrderByUpdatedAtDesc(String companyId, String userId);

    Optional<ChatSessionEntity> findByIdAndCompanyId(String id, String companyId);

    Optional<ChatSessionEntity> findByIdAndCompanyIdAndUserId(String id, String companyId, String userId);
}
