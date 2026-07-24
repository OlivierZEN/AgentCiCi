package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentAccessGrantRepository extends JpaRepository<AgentAccessGrantEntity, String> {

    List<AgentAccessGrantEntity> findByCompanyIdAndAgentIdAndStatusOrderByPrincipalTypeAscPrincipalIdAscPermissionAsc(
            String companyId, String agentId, String status);

    List<AgentAccessGrantEntity> findByCompanyIdAndAgentIdAndStatus(String companyId, String agentId, String status);
}
