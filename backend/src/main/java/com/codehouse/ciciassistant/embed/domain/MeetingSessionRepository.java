package com.codehouse.ciciassistant.embed.domain;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingSessionRepository extends JpaRepository<MeetingSessionEntity, String> {

    Optional<MeetingSessionEntity> findByTokenNonce(String tokenNonce);

    Optional<MeetingSessionEntity> findByIdAndCompanyId(String id, String companyId);

    List<MeetingSessionEntity> findByCompanyIdAndAppCodeOrderByUpdatedAtDesc(String companyId, String appCode, Pageable pageable);
}
