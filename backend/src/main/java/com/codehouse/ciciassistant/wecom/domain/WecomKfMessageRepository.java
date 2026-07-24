package com.codehouse.ciciassistant.wecom.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WecomKfMessageRepository extends JpaRepository<WecomKfMessageEntity, Long> {

    boolean existsByCompanyIdAndMsgId(String companyId, String msgId);
}
