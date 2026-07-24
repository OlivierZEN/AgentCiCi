package com.codehouse.ciciassistant.agent.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTaskRunRepository extends JpaRepository<AgentTaskRunEntity, Long> {
    Optional<AgentTaskRunEntity> findByIdAndCompanyId(Long id, String companyId);
}
