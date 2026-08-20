package com.codehouse.ciciassistant.wecom.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WecomKfConversationRepository extends JpaRepository<WecomKfConversationEntity, Long> {

    Optional<WecomKfConversationEntity> findByCompanyIdAndCorpIdAndOpenKfIdAndExternalUserId(
            String companyId,
            String corpId,
            String openKfId,
            String externalUserId);

    Optional<WecomKfConversationEntity> findByPublicIdAndCompanyIdAndOpenKfId(
            UUID publicId,
            String companyId,
            String openKfId);

    List<WecomKfConversationEntity> findTop100ByCompanyIdAndOpenKfIdOrderByUpdatedAtDesc(
            String companyId,
            String openKfId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WecomKfConversationEntity c where c.id = :id")
    Optional<WecomKfConversationEntity> findByIdForUpdate(@Param("id") Long id);
}
