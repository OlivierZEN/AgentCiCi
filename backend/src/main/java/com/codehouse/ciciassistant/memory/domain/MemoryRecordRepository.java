package com.codehouse.ciciassistant.memory.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryRecordRepository extends JpaRepository<MemoryRecordEntity, Long> {

    List<MemoryRecordEntity> findByOrgIdAndSubjectIdAndStatusInOrderByUpdatedAtDesc(
            String orgId, Long subjectId, Collection<String> statuses);
}
