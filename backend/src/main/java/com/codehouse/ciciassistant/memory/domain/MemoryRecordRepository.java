package com.codehouse.ciciassistant.memory.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryRecordRepository extends JpaRepository<MemoryRecordEntity, Long> {

    List<MemoryRecordEntity> findByCompanyIdAndSubjectIdAndStatusInOrderByUpdatedAtDesc(
            String companyId, Long subjectId, Collection<String> statuses);

    List<MemoryRecordEntity> findByCompanyIdAndSubjectId(String companyId, Long subjectId);

    List<MemoryRecordEntity> findByCompanyIdAndAgentIdOrderByUpdatedAtDesc(String companyId, String agentId);

    java.util.Optional<MemoryRecordEntity> findByIdAndCompanyIdAndAgentId(Long id, String companyId, String agentId);

    List<MemoryRecordEntity> findByStatusInAndValidToBefore(Collection<String> statuses, java.time.Instant cutoff);
}
