package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvalSuiteRepository extends JpaRepository<AgentEvalSuiteEntity, Long> {

    List<AgentEvalSuiteEntity> findByOrgIdAndAgentIdAndStatusOrderByIdAsc(String orgId, String agentId, String status);

    List<AgentEvalSuiteEntity> findByOrgIdAndStatusOrderByUpdatedAtDesc(String orgId, String status);

    List<AgentEvalSuiteEntity> findByScopeTypeNotAndReleaseStatusAndStatusOrderByIdAsc(
            String scopeType, String releaseStatus, String status);

    List<AgentEvalSuiteEntity> findByOrgIdOrderByUpdatedAtDesc(String orgId);

    List<AgentEvalSuiteEntity> findByTemplateCodeOrderByVersionNoDesc(String templateCode);

    Optional<AgentEvalSuiteEntity> findByIdAndOrgId(Long id, String orgId);
}
