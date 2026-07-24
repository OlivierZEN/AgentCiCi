package com.codehouse.ciciassistant.userworkflow.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWorkflowTriggerRepository extends JpaRepository<UserWorkflowTriggerEntity, Long> {

    List<UserWorkflowTriggerEntity> findByCompanyIdAndUserIdAndAgentIdOrderByIdAsc(String companyId, String userId, String agentId);

    List<UserWorkflowTriggerEntity> findTop100ByEnabledTrueAndNextFireAtLessThanEqualOrderByNextFireAtAsc(Instant now);

    Optional<UserWorkflowTriggerEntity> findByIdAndCompanyIdAndUserIdAndAgentId(Long id, String companyId, String userId, String agentId);

    void deleteByCompanyIdAndUserIdAndAgentId(String companyId, String userId, String agentId);

    Optional<UserWorkflowTriggerEntity> findByCompanyIdAndUserIdAndAgentIdAndRoutineKey(String companyId, String userId, String agentId, String routineKey);
}
