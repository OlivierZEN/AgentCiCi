package com.codehouse.ciciassistant.memory.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryRecordRepository extends JpaRepository<MemoryRecordEntity, Long> {

    List<MemoryRecordEntity> findByOrgIdAndSubjectIdAndStatusInOrderByUpdatedAtDesc(
            String orgId, Long subjectId, Collection<String> statuses);

    List<MemoryRecordEntity> findByOrgIdAndSubjectId(String orgId, Long subjectId);

    List<MemoryRecordEntity> findByOrgIdAndAgentIdOrderByUpdatedAtDesc(String orgId, String agentId);

    java.util.Optional<MemoryRecordEntity> findByIdAndOrgIdAndAgentId(Long id, String orgId, String agentId);

    List<MemoryRecordEntity> findByStatusInAndValidToBefore(Collection<String> statuses, java.time.Instant cutoff);
}
