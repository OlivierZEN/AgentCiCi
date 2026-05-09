package com.codehouse.ciciassistant.wecom.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WecomKfMessageRepository extends JpaRepository<WecomKfMessageEntity, Long> {

    boolean existsByOrgIdAndMsgId(String orgId, String msgId);
}
