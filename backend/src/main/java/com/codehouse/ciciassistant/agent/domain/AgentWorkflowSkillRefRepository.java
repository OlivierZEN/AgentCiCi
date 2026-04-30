package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentWorkflowSkillRefRepository extends JpaRepository<AgentWorkflowSkillRefEntity, Long> {

    boolean existsByOrgIdAndWorkflowVersionId(String orgId, Long workflowVersionId);

    List<AgentWorkflowSkillRefEntity> findByOrgIdAndWorkflowVersionIdOrderByIdAsc(String orgId, Long workflowVersionId);

    List<AgentWorkflowSkillRefEntity> findByOrgIdAndTemplateCodeOrderByTemplateVersionNoDescIdAsc(String orgId,
                                                                                                   String templateCode);
}
