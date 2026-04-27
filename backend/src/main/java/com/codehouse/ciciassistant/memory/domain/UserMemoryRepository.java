package com.codehouse.ciciassistant.memory.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMemoryRepository extends JpaRepository<UserMemoryEntity, Long> {

    List<UserMemoryEntity> findByOrgIdAndUserIdAndAgentIdOrderByPinnedDescUpdatedAtDesc(
            String orgId, String userId, String agentId);

    List<UserMemoryEntity> findByOrgIdAndUserIdAndAgentIdAndEnabledTrueOrderByPinnedDescUpdatedAtDesc(
            String orgId, String userId, String agentId);

    Optional<UserMemoryEntity> findByOrgIdAndUserIdAndAgentIdAndMemoryKey(
            String orgId, String userId, String agentId, String memoryKey);

    Optional<UserMemoryEntity> findByIdAndOrgIdAndUserId(Long id, String orgId, String userId);

    void deleteAllByOrgIdAndUserIdAndAgentId(String orgId, String userId, String agentId);
}
