package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentAccessGrantRepository extends JpaRepository<AgentAccessGrantEntity, String> {

    List<AgentAccessGrantEntity> findByOrgIdAndAgentIdAndStatusOrderByPrincipalTypeAscPrincipalIdAscPermissionAsc(
            String orgId, String agentId, String status);

    List<AgentAccessGrantEntity> findByOrgIdAndAgentIdAndStatus(String orgId, String agentId, String status);
}
