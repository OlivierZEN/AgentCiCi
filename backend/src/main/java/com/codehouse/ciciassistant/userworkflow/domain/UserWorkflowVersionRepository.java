package com.codehouse.ciciassistant.userworkflow.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWorkflowVersionRepository extends JpaRepository<UserWorkflowVersionEntity, Long> {

    List<UserWorkflowVersionEntity> findByOrgIdAndUserIdAndAgentIdOrderByVersionNoDesc(String orgId, String userId, String agentId);

    Optional<UserWorkflowVersionEntity> findTopByOrgIdAndUserIdAndAgentIdOrderByVersionNoDesc(String orgId, String userId, String agentId);

    Optional<UserWorkflowVersionEntity> findByOrgIdAndUserIdAndAgentIdAndVersionNo(String orgId, String userId, String agentId, Integer versionNo);

    Optional<UserWorkflowVersionEntity> findByOrgIdAndUserIdAndAgentIdAndPublishStatus(String orgId, String userId, String agentId, String publishStatus);
}
