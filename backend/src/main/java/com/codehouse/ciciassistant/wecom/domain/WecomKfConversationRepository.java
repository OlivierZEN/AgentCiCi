package com.codehouse.ciciassistant.wecom.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WecomKfConversationRepository extends JpaRepository<WecomKfConversationEntity, Long> {

    Optional<WecomKfConversationEntity> findByOrgIdAndCorpIdAndOpenKfIdAndExternalUserId(
            String orgId,
            String corpId,
            String openKfId,
            String externalUserId);
}
