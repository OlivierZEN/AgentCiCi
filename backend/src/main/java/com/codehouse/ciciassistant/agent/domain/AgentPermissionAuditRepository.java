package com.codehouse.ciciassistant.agent.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentPermissionAuditRepository extends JpaRepository<AgentPermissionAuditEntity, String> {
}
