package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvalSuiteRepository extends JpaRepository<AgentEvalSuiteEntity, Long> {

    List<AgentEvalSuiteEntity> findByCompanyIdAndAgentIdAndStatusOrderByIdAsc(String companyId, String agentId, String status);

    List<AgentEvalSuiteEntity> findByCompanyIdAndStatusOrderByUpdatedAtDesc(String companyId, String status);

    List<AgentEvalSuiteEntity> findByScopeTypeNotAndReleaseStatusAndStatusOrderByIdAsc(
            String scopeType, String releaseStatus, String status);

    List<AgentEvalSuiteEntity> findByCompanyIdOrderByUpdatedAtDesc(String companyId);

    List<AgentEvalSuiteEntity> findByTemplateCodeOrderByVersionNoDesc(String templateCode);

    Optional<AgentEvalSuiteEntity> findByIdAndCompanyId(Long id, String companyId);
}
