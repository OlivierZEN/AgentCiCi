package com.codehouse.ciciassistant.embed.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebsiteVisitSessionRepository extends JpaRepository<WebsiteVisitSessionEntity, String> {

    Optional<WebsiteVisitSessionEntity> findByCompanyIdAndChatSessionId(String companyId, String chatSessionId);
}
