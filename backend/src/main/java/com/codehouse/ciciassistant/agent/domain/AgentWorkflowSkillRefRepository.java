package com.codehouse.ciciassistant.agent.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentWorkflowSkillRefRepository extends JpaRepository<AgentWorkflowSkillRefEntity, Long> {

    boolean existsByCompanyIdAndWorkflowVersionId(String companyId, Long workflowVersionId);

    List<AgentWorkflowSkillRefEntity> findByCompanyIdAndWorkflowVersionIdOrderByIdAsc(String companyId, Long workflowVersionId);

    List<AgentWorkflowSkillRefEntity> findTop1001ByCompanyIdAndSkillIdOrderBySkillVersionIdAscWorkflowVersionIdAsc(
            String companyId, Long skillId);

    List<AgentWorkflowSkillRefEntity> findByCompanyIdAndTemplateCodeOrderByTemplateVersionNoDescIdAsc(String companyId,
                                                                                                   String templateCode);

    @Query(value = """
            SELECT COUNT(*)
            FROM agent_workflow_skill_ref ref
            JOIN agent_workflow_version awv ON awv.id = ref.workflow_version_id
            JOIN agent_definition agent ON agent.company_id = ref.company_id
                AND agent.agent_id = awv.agent_id
                AND agent.published_version_id = awv.id
            WHERE ref.company_id = :companyId
                AND ref.skill_id = :skillId
                AND agent.enabled = TRUE
                AND awv.publish_status = 'PUBLISHED'
            """, nativeQuery = true)
    long countActivePublishedRuntimeByCompanyIdAndSkillId(@Param("companyId") String companyId,
                                                      @Param("skillId") Long skillId);

    boolean existsByCompanyIdAndSkillVersionId(String companyId, Long skillVersionId);
}
