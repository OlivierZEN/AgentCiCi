package com.codehouse.ciciassistant.openapi.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApiFeedbackRepository extends JpaRepository<AgentApiFeedbackEntity, Long> {

    List<AgentApiFeedbackEntity> findByMessageIdOrderByCreatedAtDesc(String messageId);
}
