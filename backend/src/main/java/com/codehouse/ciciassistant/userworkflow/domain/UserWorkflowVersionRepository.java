package com.codehouse.ciciassistant.userworkflow.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWorkflowVersionRepository extends JpaRepository<UserWorkflowVersionEntity, Long> {

    List<UserWorkflowVersionEntity> findByCompanyIdAndUserIdAndAgentIdOrderByVersionNoDesc(String companyId, String userId, String agentId);

    Optional<UserWorkflowVersionEntity> findTopByCompanyIdAndUserIdAndAgentIdOrderByVersionNoDesc(String companyId, String userId, String agentId);

    Optional<UserWorkflowVersionEntity> findByCompanyIdAndUserIdAndAgentIdAndVersionNo(String companyId, String userId, String agentId, Integer versionNo);

    Optional<UserWorkflowVersionEntity> findByCompanyIdAndUserIdAndAgentIdAndPublishStatus(String companyId, String userId, String agentId, String publishStatus);
}
