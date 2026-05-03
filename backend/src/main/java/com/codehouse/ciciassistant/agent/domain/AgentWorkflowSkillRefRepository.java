package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentWorkflowSkillRefRepository extends JpaRepository<AgentWorkflowSkillRefEntity, Long> {

    boolean existsByOrgIdAndWorkflowVersionId(String orgId, Long workflowVersionId);

    List<AgentWorkflowSkillRefEntity> findByOrgIdAndWorkflowVersionIdOrderByIdAsc(String orgId, Long workflowVersionId);

    List<AgentWorkflowSkillRefEntity> findByOrgIdAndTemplateCodeOrderByTemplateVersionNoDescIdAsc(String orgId,
                                                                                                   String templateCode);

    @Query(value = """
            SELECT COUNT(*)
            FROM agent_workflow_skill_ref ref
            JOIN agent_workflow_version awv ON awv.id = ref.workflow_version_id
            JOIN agent_definition agent ON agent.org_id = ref.org_id
                AND agent.agent_id = awv.agent_id
                AND agent.published_version_id = awv.id
            WHERE ref.org_id = :orgId
                AND ref.skill_id = :skillId
                AND agent.enabled = TRUE
                AND awv.publish_status = 'PUBLISHED'
            """, nativeQuery = true)
    long countActivePublishedRuntimeByOrgIdAndSkillId(@Param("orgId") String orgId,
                                                      @Param("skillId") Long skillId);

    boolean existsByOrgIdAndSkillVersionId(String orgId, Long skillVersionId);
}
