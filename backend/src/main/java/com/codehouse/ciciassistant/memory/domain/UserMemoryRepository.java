package com.codehouse.ciciassistant.memory.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMemoryRepository extends JpaRepository<UserMemoryEntity, Long> {

    List<UserMemoryEntity> findByCompanyIdAndUserIdAndAgentIdOrderByPinnedDescUpdatedAtDesc(
            String companyId, String userId, String agentId);

    List<UserMemoryEntity> findByCompanyIdAndUserIdAndAgentIdAndEnabledTrueOrderByPinnedDescUpdatedAtDesc(
            String companyId, String userId, String agentId);

    Optional<UserMemoryEntity> findByCompanyIdAndUserIdAndAgentIdAndMemoryKey(
            String companyId, String userId, String agentId, String memoryKey);

    Optional<UserMemoryEntity> findByIdAndCompanyIdAndUserId(Long id, String companyId, String userId);

    void deleteAllByCompanyIdAndUserIdAndAgentId(String companyId, String userId, String agentId);
}
